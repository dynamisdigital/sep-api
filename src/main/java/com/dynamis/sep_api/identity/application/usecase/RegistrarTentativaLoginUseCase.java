package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.domain.model.LoginAttempt;
import com.dynamis.sep_api.identity.domain.model.LoginAttemptStatus;
import com.dynamis.sep_api.identity.infrastructure.persistence.LoginAttemptRepository;
import com.dynamis.sep_api.shared.audit.AuditLogSeguranca;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persiste {@link LoginAttempt} para cada tentativa de login (Sprint 5 Task 5.4) e grava o evento
 * correspondente no audit log de seguranca.
 */
@Service
public class RegistrarTentativaLoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrarTentativaLoginUseCase.class);

    private final LoginAttemptRepository attemptRepository;
    private final AuditLogSegurancaRepository auditRepository;
    private final ObjectMapper objectMapper;

    public RegistrarTentativaLoginUseCase(
            LoginAttemptRepository attemptRepository,
            AuditLogSegurancaRepository auditRepository,
            ObjectMapper objectMapper) {
        this.attemptRepository = attemptRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
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
        auditRepository.save(
                AuditLogSeguranca.registrar(tipoEvento(status), usuarioId, ip, userAgent, detalhes(status, username)));
    }

    /**
     * O {@code username} vem do corpo da request e a coluna e {@code jsonb}: montar o documento por
     * concatenacao deixava um username com aspas produzir JSON invalido, que o Postgres rejeita na
     * conversao — derrubando a gravacao inteira do rastro (Sprint 34 Task 34.2). O {@code @Email} do
     * DTO nao protege: a RFC admite local-part entre aspas.
     */
    private String detalhes(LoginAttemptStatus status, String username) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("status", status.name());
        detalhes.put("username", username);
        try {
            return objectMapper.writeValueAsString(detalhes);
        } catch (JsonProcessingException ex) {
            log.warn("falha ao serializar detalhes de auditoria de tentativa de login", ex);
            return null;
        }
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
