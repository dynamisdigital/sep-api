package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.domain.model.LoginAttempt;
import com.dynamis.sep_api.identity.domain.model.LoginAttemptStatus;
import com.dynamis.sep_api.identity.infrastructure.persistence.LoginAttemptRepository;
import com.dynamis.sep_api.shared.audit.AuditLogSeguranca;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persiste {@link LoginAttempt} para cada tentativa de login (Sprint 5 Task 5.4) e grava o evento
 * correspondente no audit log de seguranca.
 */
@Service
public class RegistrarTentativaLoginUseCase {

    private final LoginAttemptRepository attemptRepository;
    private final AuditLogSegurancaRepository auditRepository;

    public RegistrarTentativaLoginUseCase(
            LoginAttemptRepository attemptRepository, AuditLogSegurancaRepository auditRepository) {
        this.attemptRepository = attemptRepository;
        this.auditRepository = auditRepository;
    }

    /**
     * Persiste a tentativa em transacao <b>propria</b> (Sprint 33).
     *
     * <p>Com a propagacao default a gravacao entrava na transacao do {@code AutenticarUsuarioUseCase}
     * e era desfeita pelo {@code BadCredentialsException} lancado logo em seguida: nenhuma falha
     * chegava a {@code login_attempt} (so {@code SUCESSO}, que nao lanca), entao o account lockout
     * nunca bloqueava e o audit de login falho tambem se perdia.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(UUID usuarioId, String username, String ip, String userAgent, LoginAttemptStatus status) {
        attemptRepository.save(LoginAttempt.registrar(usuarioId, username, ip, userAgent, status));
        auditRepository.save(AuditLogSeguranca.registrar(
                tipoEvento(status),
                usuarioId,
                ip,
                userAgent,
                "{\"status\":\"" + status.name() + "\",\"username\":\"" + username + "\"}"));
    }

    private TipoEventoSeguranca tipoEvento(LoginAttemptStatus status) {
        return switch (status) {
            case SUCESSO -> TipoEventoSeguranca.LOGIN_OK;
            case TOTP_INVALIDO -> TipoEventoSeguranca.TOTP_FAIL;
            case CONTA_BLOQUEADA -> TipoEventoSeguranca.LOCKOUT_TENTATIVA_BARRADA;
            case SENHA_INVALIDA, USUARIO_INEXISTENTE, TOTP_NECESSARIO -> TipoEventoSeguranca.LOGIN_FAIL;
        };
    }
}
