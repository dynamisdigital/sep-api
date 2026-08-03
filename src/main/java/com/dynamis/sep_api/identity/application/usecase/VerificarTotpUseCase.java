package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.ContaBloqueadaException;
import com.dynamis.sep_api.identity.application.exception.MfaNaoHabilitadoException;
import com.dynamis.sep_api.identity.application.exception.TotpInvalidoException;
import com.dynamis.sep_api.identity.application.service.BackupCodeService;
import com.dynamis.sep_api.identity.application.service.LockoutService;
import com.dynamis.sep_api.identity.application.service.MfaChallengeService;
import com.dynamis.sep_api.identity.application.service.RefreshTokenService;
import com.dynamis.sep_api.identity.application.service.RefreshTokenService.TokenCru;
import com.dynamis.sep_api.identity.domain.model.LoginAttemptStatus;
import com.dynamis.sep_api.identity.domain.model.MfaStatus;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.identity.infrastructure.security.JwtProperties;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.totp.GoogleAuthAdapter;
import com.dynamis.sep_api.identity.infrastructure.totp.TotpCryptoService;
import com.dynamis.sep_api.identity.web.dto.TokenResponseDto;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Verifica TOTP/backup code apresentado apos o login com senha (Sprint 5 Task 5.3) e integra
 * lockout (Task 5.4): falhas TOTP contam para o limite de tentativas.
 */
@Service
public class VerificarTotpUseCase {

    private static final Logger log = LoggerFactory.getLogger(VerificarTotpUseCase.class);

    private final UsuarioRepository usuarioRepository;
    private final UsuarioTotpSecretRepository totpRepository;
    private final BackupCodeService backupCodeService;
    private final GoogleAuthAdapter googleAuth;
    private final TotpCryptoService crypto;
    private final MfaChallengeService challengeService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final UsuarioMapper usuarioMapper;
    private final LockoutService lockoutService;
    private final RegistrarTentativaLoginUseCase registrarTentativaLogin;
    private final AuditLogSegurancaService auditService;

    public VerificarTotpUseCase(
            UsuarioRepository usuarioRepository,
            UsuarioTotpSecretRepository totpRepository,
            BackupCodeService backupCodeService,
            GoogleAuthAdapter googleAuth,
            TotpCryptoService crypto,
            MfaChallengeService challengeService,
            JwtTokenProvider jwtTokenProvider,
            JwtProperties jwtProperties,
            RefreshTokenService refreshTokenService,
            UsuarioMapper usuarioMapper,
            LockoutService lockoutService,
            RegistrarTentativaLoginUseCase registrarTentativaLogin,
            AuditLogSegurancaService auditService) {
        this.usuarioRepository = usuarioRepository;
        this.totpRepository = totpRepository;
        this.backupCodeService = backupCodeService;
        this.googleAuth = googleAuth;
        this.crypto = crypto;
        this.challengeService = challengeService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
        this.usuarioMapper = usuarioMapper;
        this.lockoutService = lockoutService;
        this.registrarTentativaLogin = registrarTentativaLogin;
        this.auditService = auditService;
    }

    @Transactional
    public TokenResponseDto executar(UUID mfaChallengeId, String codigo, String ip, String userAgent) {
        if (codigo == null || codigo.isBlank()) {
            throw new TotpInvalidoException();
        }
        UUID usuarioId = challengeService.consumir(mfaChallengeId);
        Usuario usuario =
                usuarioRepository.findById(usuarioId).orElseThrow(() -> new UsuarioNaoEncontradoException(usuarioId));
        verificarLockout(usuarioId, usuario.getUsername(), ip, userAgent);

        UsuarioTotpSecret secret =
                totpRepository.findByUsuarioId(usuarioId).orElseThrow(MfaNaoHabilitadoException::new);
        if (secret.getStatus() != MfaStatus.ATIVO) {
            throw new MfaNaoHabilitadoException();
        }

        boolean ok = false;
        boolean usouBackupCode = false;
        if (ehCodigoTotp(codigo)) {
            int numerico = Integer.parseInt(codigo);
            String secretClaro = crypto.decifrar(secret.getSecretCifrado());
            ok = googleAuth.validarCodigo(secretClaro, numerico);
        }
        if (!ok) {
            ok = backupCodeService.consumir(usuarioId, codigo);
            usouBackupCode = ok;
        }
        if (!ok) {
            challengeService.devolver(mfaChallengeId, usuarioId);
            registrarTentativaLogin.registrar(
                    usuarioId, usuario.getUsername(), ip, userAgent, LoginAttemptStatus.TOTP_INVALIDO);
            lockoutService.avaliarPosFalha(usuarioId, usuario.getUsername());
            throw new TotpInvalidoException();
        }

        registrarTentativaLogin.registrar(usuarioId, usuario.getUsername(), ip, userAgent, LoginAttemptStatus.SUCESSO);
        auditService.gravar(
                usouBackupCode ? TipoEventoSeguranca.BACKUP_CODE_USED : TipoEventoSeguranca.TOTP_OK, usuarioId);
        String access = jwtTokenProvider.gerarToken(usuario);
        TokenCru refresh = refreshTokenService.emitirParaNovoLogin(usuario.getId());
        return TokenResponseDto.comTokens(
                access, jwtProperties.getAccessExpirationSeconds(), refresh.token(), usuarioMapper.toResponse(usuario));
    }

    /**
     * Recusa a verificacao de segundo fator e <b>deixa rastro da recusa</b> (Sprint 34 Task 34.1).
     * Ate a Sprint 33 nada era gravado neste caminho: {@code verificar} lanca antes de qualquer
     * {@code registrar}.
     *
     * <p>Aqui o usuario ja esta resolvido pelo challenge, entao o rastro nao custa leitura extra. O
     * registro sobrevive ao rollback desta transacao por rodar em {@code REQUIRES_NEW}.
     *
     * <p>Falha ao gravar o rastro <b>nao</b> derruba a decisao: sem isolar, a excecao do registro
     * substituiria a {@code ContaBloqueadaException} e o cliente receberia 500 no lugar de 423 —
     * justamente sob a carga que esta observabilidade existe para enxergar.
     */
    private void verificarLockout(UUID usuarioId, String username, String ip, String userAgent) {
        try {
            lockoutService.verificar(username);
        } catch (ContaBloqueadaException e) {
            registrarTentativaBarrada(usuarioId, username, ip, userAgent);
            throw e;
        }
    }

    private void registrarTentativaBarrada(UUID usuarioId, String username, String ip, String userAgent) {
        try {
            registrarTentativaLogin.registrar(usuarioId, username, ip, userAgent, LoginAttemptStatus.CONTA_BLOQUEADA);
        } catch (RuntimeException falhaDoRastro) {
            log.atWarn()
                    .setCause(falhaDoRastro)
                    .addKeyValue("event", "lockout_attempt_audit_failed")
                    .log("Falha ao registrar tentativa contra conta bloqueada");
        }
    }

    private boolean ehCodigoTotp(String codigo) {
        return codigo.length() == 6 && codigo.chars().allMatch(Character::isDigit);
    }
}
