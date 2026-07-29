package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.application.exception.ContaBloqueadaException;
import com.dynamis.sep_api.identity.domain.model.LoginAttemptStatus;
import com.dynamis.sep_api.identity.domain.model.PoliticaLockout;
import com.dynamis.sep_api.identity.infrastructure.persistence.LoginAttemptRepository;
import com.dynamis.sep_api.identity.infrastructure.security.LockoutProperties;
import com.dynamis.sep_api.shared.audit.AuditLogSeguranca;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.shared.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Politica de account lockout (Sprint 5 Task 5.4; conformidade na Sprint 33).
 *
 * <p>Apos {@link LockoutProperties#getMaxAttempts()} (default 5) tentativas falhas dentro de {@link
 * LockoutProperties#getWindowMinutes()} (default 15 min), a conta fica bloqueada por {@link
 * LockoutProperties#getLockoutMinutes()} (default 30 min) contados a partir da falha que fechou a
 * janela.
 *
 * <p>A decisao vive em {@link PoliticaLockout}; aqui fica so a leitura do historico. Ate a Sprint 33
 * o bloqueio era aproximado por contagem na janela de 30 min, o que bloqueava 5 falhas espalhadas
 * por 25 minutos e fazia o desbloqueio depender do envelhecimento das falhas, nao do bloqueio.
 */
@Service
public class LockoutService {

    private static final Logger log = LoggerFactory.getLogger(LockoutService.class);
    private static final List<LoginAttemptStatus> STATUSES_FALHA = List.of(
            LoginAttemptStatus.SENHA_INVALIDA, LoginAttemptStatus.TOTP_INVALIDO, LoginAttemptStatus.CONTA_BLOQUEADA);

    /**
     * Teto defensivo de falhas lidas por decisao. Nao limita a politica: uma janela que bloqueie
     * sempre estara entre as falhas mais recentes.
     */
    private static final int LIMITE_DE_LEITURA = 100;

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
        return eventoDeBloqueio(username, OffsetDateTime.now()).isPresent();
    }

    /** Instante da falha que bloqueou a conta, se o bloqueio ainda estiver valendo em {@code agora}. */
    private Optional<OffsetDateTime> eventoDeBloqueio(String username, OffsetDateTime agora) {
        PoliticaLockout politica = politica();
        OffsetDateTime inicioDaLeitura = agora.minus(politica.janelaDeLeitura());
        List<OffsetDateTime> falhas = attemptRepository.buscarInstantesDeFalha(
                username, STATUSES_FALHA, inicioDaLeitura, PageRequest.of(0, LIMITE_DE_LEITURA));
        return politica.eventoDeBloqueio(falhas, agora);
    }

    private PoliticaLockout politica() {
        return new PoliticaLockout(
                properties.getMaxAttempts(),
                Duration.ofMinutes(properties.getWindowMinutes()),
                Duration.ofMinutes(properties.getLockoutMinutes()));
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
