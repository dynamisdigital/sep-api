package com.dynamis.sep_api.usuarios.application.listener;

import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.usuarios.domain.event.RoleAlteradaEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Liga eventos de dominio do modulo {@code usuarios} ao {@code audit_log_seguranca} (Sprint 8
 * fix code review Task 8.6 — padronizacao consistente com {@code OnboardingAuditListener} e
 * {@code CreditoAuditListener}: {@link TransactionPhase#AFTER_COMMIT} + {@link
 * Propagation#REQUIRES_NEW}).
 */
@Component
public class UsuariosAuditListener {

    private static final Logger log = LoggerFactory.getLogger(UsuariosAuditListener.class);

    private final AuditLogSegurancaService auditLogService;
    private final ObjectMapper objectMapper;

    public UsuariosAuditListener(AuditLogSegurancaService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAlterarRole(RoleAlteradaEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("usuarioAlvoId", event.usuarioAlvoId().toString());
        payload.put("roleAnterior", event.roleAnterior().name());
        payload.put("roleNova", event.roleNova().name());
        auditLogService.gravar(TipoEventoSeguranca.ROLE_ALTERADO, event.atorAdminId(), serializar(payload));
    }

    private String serializar(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Falha ao serializar detalhes de audit log de usuarios: {}", ex.getMessage());
            return "{}";
        }
    }
}
