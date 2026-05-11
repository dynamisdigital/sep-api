package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.MfaNaoHabilitadoException;
import com.dynamis.sep_api.identity.application.exception.TotpInvalidoException;
import com.dynamis.sep_api.identity.application.service.BackupCodeService;
import com.dynamis.sep_api.identity.domain.model.MfaStatus;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.identity.infrastructure.totp.GoogleAuthAdapter;
import com.dynamis.sep_api.identity.infrastructure.totp.TotpCryptoService;
import com.dynamis.sep_api.identity.web.dto.TotpVerifyResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Verifica TOTP ou backup code durante o login (apos senha valida). Codigo de 6 digitos vai pelo
 * adapter TOTP; qualquer outro formato e tentado como backup code (consumo unico).
 *
 * <p>Em falha, lanca {@link TotpInvalidoException}. Rate limit por usuario/challenge entra na Task
 * 5.4.
 */
@Service
public class VerificarTotpUseCase {

    private final UsuarioTotpSecretRepository totpRepository;
    private final BackupCodeService backupCodeService;
    private final GoogleAuthAdapter googleAuth;
    private final TotpCryptoService crypto;

    public VerificarTotpUseCase(
            UsuarioTotpSecretRepository totpRepository,
            BackupCodeService backupCodeService,
            GoogleAuthAdapter googleAuth,
            TotpCryptoService crypto) {
        this.totpRepository = totpRepository;
        this.backupCodeService = backupCodeService;
        this.googleAuth = googleAuth;
        this.crypto = crypto;
    }

    @Transactional
    public TotpVerifyResponseDto executar(UUID usuarioId, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new TotpInvalidoException();
        }

        UsuarioTotpSecret secret =
                totpRepository.findByUsuarioId(usuarioId).orElseThrow(MfaNaoHabilitadoException::new);
        if (secret.getStatus() != MfaStatus.ATIVO) {
            throw new MfaNaoHabilitadoException();
        }

        if (ehCodigoTotp(codigo)) {
            int numerico = Integer.parseInt(codigo);
            String secretClaro = crypto.decifrar(secret.getSecretCifrado());
            if (googleAuth.validarCodigo(secretClaro, numerico)) {
                return new TotpVerifyResponseDto(true, false);
            }
        }

        if (backupCodeService.consumir(usuarioId, codigo)) {
            return new TotpVerifyResponseDto(true, true);
        }

        throw new TotpInvalidoException();
    }

    private boolean ehCodigoTotp(String codigo) {
        return codigo.length() == 6 && codigo.chars().allMatch(Character::isDigit);
    }
}
