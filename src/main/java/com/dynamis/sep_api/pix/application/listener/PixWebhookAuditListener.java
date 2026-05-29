package com.dynamis.sep_api.pix.application.listener;

import com.dynamis.sep_api.pix.domain.event.PixWebhookFalhouEvent;
import com.dynamis.sep_api.pix.domain.event.PixWebhookProcessadoEvent;
import com.dynamis.sep_api.pix.domain.event.PixWebhookRecebidoEvent;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
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
 * Liga os eventos de webhook Pix ao {@code audit_log_seguranca} (Sprint 19 Task 19.6).
 *
 * <p>{@link TransactionalEventListener} com {@link TransactionPhase#AFTER_COMMIT} garante que
 * eventos de transacoes revertidas nao gerem trilha. Handlers usam {@link Propagation#REQUIRES_NEW},
 * alinhado a {@code CobrancaAuditListener}.
 *
 * <p>Webhook nao tem usuario autenticado (autenticacao por HMAC) — {@code usuarioId} fica nulo. Os
 * detalhes JSON carregam APENAS identificadores tecnicos (event_id, tipo, provider): nunca o
 * payload bruto, hash de payload ou dados bancarios.
 */
@Component
public class PixWebhookAuditListener {

    private static final Logger log = LoggerFactory.getLogger(PixWebhookAuditListener.class);
    private static final String PROVIDER = "celcoin-pix";

    private final AuditLogSegurancaService auditLogService;
    private final ObjectMapper objectMapper;

    public PixWebhookAuditListener(AuditLogSegurancaService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoReceber(PixWebhookRecebidoEvent event) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("eventId", event.eventId());
        detalhes.put("tipo", event.tipo().name());
        detalhes.put("provider", PROVIDER);
        auditLogService.gravar(TipoEventoSeguranca.PIX_WEBHOOK_RECEBIDO, null, serializar(detalhes, event.eventId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoProcessar(PixWebhookProcessadoEvent event) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("eventId", event.eventId());
        detalhes.put("tipo", event.tipo().name());
        detalhes.put("provider", PROVIDER);
        auditLogService.gravar(TipoEventoSeguranca.PIX_WEBHOOK_PROCESSADO, null, serializar(detalhes, event.eventId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoFalhar(PixWebhookFalhouEvent event) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("eventId", event.eventId());
        detalhes.put("motivo", event.motivo());
        detalhes.put("provider", PROVIDER);
        auditLogService.gravar(TipoEventoSeguranca.PIX_WEBHOOK_FALHOU, null, serializar(detalhes, event.eventId()));
    }

    private String serializar(Map<String, Object> detalhes, String eventId) {
        try {
            return objectMapper.writeValueAsString(detalhes);
        } catch (JsonProcessingException ex) {
            log.warn("Falha ao serializar detalhes de audit do webhook Pix eventId={}", eventId);
            return "{}";
        }
    }
}
