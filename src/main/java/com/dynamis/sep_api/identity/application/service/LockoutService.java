package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.application.exception.ContaBloqueadaException;
import com.dynamis.sep_api.identity.domain.model.LoginAttemptStatus;
import com.dynamis.sep_api.identity.infrastructure.persistence.LoginAttemptRepository;
import com.dynamis.sep_api.identity.infrastructure.security.LockoutProperties;
import com.dynamis.sep_api.shared.audit.AuditLogSeguranca;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.shared.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Politica de account lockout (Sprint 5 Task 5.4).
 *
 * <p>Janela de detecao: {@link LockoutProperties#getWindowMinutes()} (default 15 min). Apos {@link
 * LockoutProperties#getMaxAttempts()} (default 5) tentativas falhas, a conta entra em lockout pelo
 * tempo configurado em {@link LockoutProperties#getLockoutMinutes()} (default 30 min).
 *
 * <p>Implementacao: lockout ativo quando ha falhas suficientes nos ultimos {@code lockoutMinutes};
 * janela de detecao curta (15 min) acumula falhas e a propria contagem nos ultimos {@code
 * lockoutMinutes} mantem o bloqueio ate expirar.
 */
@Service
public class LockoutService {

    private static final Logger log = LoggerFactory.getLogger(LockoutService.class);
    private static final List<LoginAttemptStatus> STATUSES_FALHA = List.of(
            LoginAttemptStatus.SENHA_INVALIDA, LoginAttemptStatus.TOTP_INVALIDO, LoginAttemptStatus.CONTA_BLOQUEADA);

    private final LoginAttemptRepository attemptRepository;
    private final AuditLogSegurancaRepository auditRepository;
    private final LockoutProperties properties;
    private final EmailService emailService;

    public LockoutService(
            LoginAttemptRepository attemptRepository,
            AuditLogSegurancaRepository auditRepository,
            LockoutProperties properties,
            EmailService emailService) {
        this.attemptRepository = attemptRepository;
        this.auditRepository = auditRepository;
        this.properties = properties;
        this.emailService = emailService;
    }

    /** Falha se a conta estiver atualmente bloqueada. */
    @Transactional(readOnly = true)
    public void verificar(String username) {
        if (estaBloqueada(username)) {
            throw new ContaBloqueadaException(properties.getLockoutMinutes());
        }
    }

    public boolean estaBloqueada(String username) {
        OffsetDateTime inicio = OffsetDateTime.now().minusMinutes(properties.getLockoutMinutes());
        long falhas = attemptRepository.countByUsernameAndStatusInAndJanela(username, STATUSES_FALHA, inicio);
        return falhas >= properties.getMaxAttempts();
    }

    /**
     * Avalia se uma falha recem-registrada acabou de cruzar o limite e, em caso afirmativo, emite
     * email + audit log de LOCKOUT. Deve ser chamado pelo {@code AutenticarUsuarioUseCase} apos
     * persistir a tentativa falha.
     */
    @Transactional
    public void avaliarPosFalha(UUID usuarioId, String username) {
        OffsetDateTime inicioJanelaDetecao = OffsetDateTime.now().minusMinutes(properties.getWindowMinutes());
        long falhasJanela =
                attemptRepository.countByUsernameAndStatusInAndJanela(username, STATUSES_FALHA, inicioJanelaDetecao);
        if (falhasJanela == properties.getMaxAttempts()) {
            log.atWarn()
                    .addKeyValue("event", "account_lockout")
                    .addKeyValue("durationMinutes", properties.getLockoutMinutes())
                    .log("Conta entrou em lockout");
            auditRepository.save(AuditLogSeguranca.registrar(
                    TipoEventoSeguranca.LOCKOUT,
                    usuarioId,
                    null,
                    null,
                    "{\"username\":\"" + username + "\",\"lockoutMinutes\":" + properties.getLockoutMinutes() + "}"));
            emailService.enviar(
                    username,
                    "Conta SEP bloqueada temporariamente",
                    "Detectamos varias tentativas de login. Sua conta esta bloqueada por "
                            + properties.getLockoutMinutes() + " minutos.");
        }
    }
}
