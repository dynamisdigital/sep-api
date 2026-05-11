package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.MfaNaoHabilitadoException;
import com.dynamis.sep_api.identity.domain.model.MfaStatus;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioBackupCodeRepository;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.usuarios.application.exception.SenhaAtualIncorretaException;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Desabilita o TOTP do usuario. Exige senha atual valida; step-up token sera exigido a partir da
 * Task 5.6.
 */
@Service
public class DesabilitarTotpUseCase {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioTotpSecretRepository totpRepository;
    private final UsuarioBackupCodeRepository backupCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogSegurancaService auditService;

    public DesabilitarTotpUseCase(
            UsuarioRepository usuarioRepository,
            UsuarioTotpSecretRepository totpRepository,
            UsuarioBackupCodeRepository backupCodeRepository,
            PasswordEncoder passwordEncoder,
            AuditLogSegurancaService auditService) {
        this.usuarioRepository = usuarioRepository;
        this.totpRepository = totpRepository;
        this.backupCodeRepository = backupCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public void executar(UUID usuarioId, String passwordAtual) {
        Usuario usuario =
                usuarioRepository.findById(usuarioId).orElseThrow(() -> new UsuarioNaoEncontradoException(usuarioId));
        if (!passwordEncoder.matches(passwordAtual, usuario.getPassword())) {
            throw new SenhaAtualIncorretaException();
        }

        UsuarioTotpSecret secret =
                totpRepository.findByUsuarioId(usuarioId).orElseThrow(MfaNaoHabilitadoException::new);
        if (secret.getStatus() != MfaStatus.ATIVO) {
            throw new MfaNaoHabilitadoException();
        }

        secret.desabilitar();
        totpRepository.save(secret);
        backupCodeRepository.deleteByUsuarioId(usuarioId);
        usuario.marcarMfaDesabilitado();
        usuarioRepository.save(usuario);
        auditService.gravar(TipoEventoSeguranca.MFA_DISABLED, usuarioId);
    }
}
