package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.MfaJaHabilitadoException;
import com.dynamis.sep_api.identity.application.exception.TotpInvalidoException;
import com.dynamis.sep_api.identity.application.exception.TotpSetupNaoIniciadoException;
import com.dynamis.sep_api.identity.domain.model.MfaStatus;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.identity.infrastructure.totp.GoogleAuthAdapter;
import com.dynamis.sep_api.identity.infrastructure.totp.TotpCryptoService;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Confirma o primeiro codigo TOTP gerado pelo usuario apos {@link HabilitarTotpUseCase}. Em
 * sucesso, o secret passa de {@link MfaStatus#PENDENTE} para {@link MfaStatus#ATIVO}.
 */
@Service
public class ConfirmarTotpUseCase {

    private final UsuarioTotpSecretRepository totpRepository;
    private final GoogleAuthAdapter googleAuth;
    private final TotpCryptoService crypto;
    private final UsuarioRepository usuarioRepository;
    private final AuditLogSegurancaService auditService;

    public ConfirmarTotpUseCase(
            UsuarioTotpSecretRepository totpRepository,
            GoogleAuthAdapter googleAuth,
            TotpCryptoService crypto,
            UsuarioRepository usuarioRepository,
            AuditLogSegurancaService auditService) {
        this.totpRepository = totpRepository;
        this.googleAuth = googleAuth;
        this.crypto = crypto;
        this.usuarioRepository = usuarioRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void executar(UUID usuarioId, String codigoSeisDigitos) {
        UsuarioTotpSecret secret =
                totpRepository.findByUsuarioId(usuarioId).orElseThrow(TotpSetupNaoIniciadoException::new);

        if (secret.getStatus() == MfaStatus.ATIVO) {
            throw new MfaJaHabilitadoException();
        }
        if (secret.getStatus() != MfaStatus.PENDENTE) {
            throw new TotpSetupNaoIniciadoException();
        }

        int codigo = parsearCodigo(codigoSeisDigitos);
        String secretClaro = crypto.decifrar(secret.getSecretCifrado());
        if (!googleAuth.validarCodigo(secretClaro, codigo)) {
            throw new TotpInvalidoException();
        }

        secret.ativar();
        totpRepository.save(secret);

        Usuario usuario =
                usuarioRepository.findById(usuarioId).orElseThrow(() -> new UsuarioNaoEncontradoException(usuarioId));
        usuario.marcarMfaHabilitado();
        usuarioRepository.save(usuario);
        auditService.gravar(TipoEventoSeguranca.MFA_ENABLED, usuarioId);
    }

    private int parsearCodigo(String codigo) {
        try {
            return Integer.parseInt(codigo);
        } catch (NumberFormatException ex) {
            throw new TotpInvalidoException();
        }
    }
}
