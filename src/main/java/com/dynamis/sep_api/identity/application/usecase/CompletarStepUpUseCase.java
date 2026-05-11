package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.MfaNaoHabilitadoException;
import com.dynamis.sep_api.identity.application.exception.TotpInvalidoException;
import com.dynamis.sep_api.identity.application.service.BackupCodeService;
import com.dynamis.sep_api.identity.application.service.StepUpChallengeService;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService.TokenCru;
import com.dynamis.sep_api.identity.domain.model.MfaStatus;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.identity.infrastructure.totp.GoogleAuthAdapter;
import com.dynamis.sep_api.identity.infrastructure.totp.TotpCryptoService;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Conclui step-up apresentando codigo TOTP (ou backup code) (Sprint 5 Task 5.6). O step-up token
 * resultante deve ser enviado pelo cliente em {@code X-Step-Up-Token} nas requests subsequentes a
 * endpoints anotados com {@code @RequireStepUp}.
 *
 * <p>Verifica que o {@code usuarioId} do challenge bate com o usuario autenticado para impedir que
 * um challenge alheio seja resolvido.
 */
@Service
public class CompletarStepUpUseCase {

    private final UsuarioTotpSecretRepository totpRepository;
    private final BackupCodeService backupCodeService;
    private final GoogleAuthAdapter googleAuth;
    private final TotpCryptoService crypto;
    private final StepUpChallengeService challengeService;
    private final StepUpTokenService tokenService;
    private final AuditLogSegurancaService auditService;

    public CompletarStepUpUseCase(
            UsuarioTotpSecretRepository totpRepository,
            BackupCodeService backupCodeService,
            GoogleAuthAdapter googleAuth,
            TotpCryptoService crypto,
            StepUpChallengeService challengeService,
            StepUpTokenService tokenService,
            AuditLogSegurancaService auditService) {
        this.totpRepository = totpRepository;
        this.backupCodeService = backupCodeService;
        this.googleAuth = googleAuth;
        this.crypto = crypto;
        this.challengeService = challengeService;
        this.tokenService = tokenService;
        this.auditService = auditService;
    }

    @Transactional
    public String executar(UUID challengeId, String codigo, UUID usuarioAutenticadoId) {
        if (codigo == null || codigo.isBlank()) {
            throw new TotpInvalidoException();
        }
        UUID usuarioId = challengeService.consumir(challengeId);
        if (!usuarioId.equals(usuarioAutenticadoId)) {
            throw new AccessDeniedException("Challenge nao pertence ao usuario autenticado");
        }

        UsuarioTotpSecret secret =
                totpRepository.findByUsuarioId(usuarioId).orElseThrow(MfaNaoHabilitadoException::new);
        if (secret.getStatus() != MfaStatus.ATIVO) {
            throw new MfaNaoHabilitadoException();
        }

        boolean ok = false;
        if (ehCodigoTotp(codigo)) {
            int numerico = Integer.parseInt(codigo);
            String secretClaro = crypto.decifrar(secret.getSecretCifrado());
            ok = googleAuth.validarCodigo(secretClaro, numerico);
        }
        if (!ok) {
            ok = backupCodeService.consumir(usuarioId, codigo);
        }
        if (!ok) {
            auditService.gravar(TipoEventoSeguranca.STEP_UP_FAIL, usuarioId);
            throw new TotpInvalidoException();
        }

        TokenCru cru = tokenService.emitir(usuarioId);
        auditService.gravar(TipoEventoSeguranca.STEP_UP_OK, usuarioId);
        return cru.token();
    }

    private boolean ehCodigoTotp(String codigo) {
        return codigo.length() == 6 && codigo.chars().allMatch(Character::isDigit);
    }
}
