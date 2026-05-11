package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.MfaJaHabilitadoException;
import com.dynamis.sep_api.identity.application.service.BackupCodeService;
import com.dynamis.sep_api.identity.domain.model.MfaStatus;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.identity.infrastructure.totp.GoogleAuthAdapter;
import com.dynamis.sep_api.identity.infrastructure.totp.TotpCryptoService;
import com.dynamis.sep_api.identity.web.dto.TotpSetupResponseDto;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inicia setup de TOTP: gera secret, cifra, persiste como PENDENTE, gera 10 backup codes claros e
 * monta a URI {@code otpauth://} + QR code data URL.
 *
 * <p>Se ja existe secret PENDENTE, sobrescreve (usuario abortou setup anterior); se existe ATIVO,
 * lanca {@link MfaJaHabilitadoException}. Operacao idempotente para PENDENTE.
 */
@Service
public class HabilitarTotpUseCase {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioTotpSecretRepository totpRepository;
    private final GoogleAuthAdapter googleAuth;
    private final TotpCryptoService crypto;
    private final BackupCodeService backupCodeService;

    public HabilitarTotpUseCase(
            UsuarioRepository usuarioRepository,
            UsuarioTotpSecretRepository totpRepository,
            GoogleAuthAdapter googleAuth,
            TotpCryptoService crypto,
            BackupCodeService backupCodeService) {
        this.usuarioRepository = usuarioRepository;
        this.totpRepository = totpRepository;
        this.googleAuth = googleAuth;
        this.crypto = crypto;
        this.backupCodeService = backupCodeService;
    }

    @Transactional
    public TotpSetupResponseDto executar(UUID usuarioId) {
        Usuario usuario =
                usuarioRepository.findById(usuarioId).orElseThrow(() -> new UsuarioNaoEncontradoException(usuarioId));

        Optional<UsuarioTotpSecret> existente = totpRepository.findByUsuarioId(usuarioId);
        existente.ifPresent(secret -> {
            if (secret.getStatus() == MfaStatus.ATIVO) {
                throw new MfaJaHabilitadoException();
            }
            totpRepository.delete(secret);
            totpRepository.flush();
        });

        String secretBase32 = googleAuth.gerarSecretBase32();
        String secretCifrado = crypto.cifrar(secretBase32);

        UsuarioTotpSecret novo = UsuarioTotpSecret.iniciar(usuarioId, secretCifrado);
        totpRepository.save(novo);

        String otpAuthUri = googleAuth.gerarOtpAuthUri(usuario.getUsername(), secretBase32);
        String qrCode = googleAuth.gerarQrCodeDataUrl(otpAuthUri);
        List<String> backupCodes = backupCodeService.gerarParaUsuario(usuarioId);

        return new TotpSetupResponseDto(secretBase32, otpAuthUri, qrCode, backupCodes);
    }
}
