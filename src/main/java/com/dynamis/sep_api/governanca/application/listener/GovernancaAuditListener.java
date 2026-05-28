package com.dynamis.sep_api.governanca.application.listener;

import com.dynamis.sep_api.governanca.domain.event.ParametroOperacionalAlteradoEvent;
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
 * Liga eventos de dominio do modulo {@code governanca} ao {@code audit_log_seguranca} (Sprint 18
 * Task 18.6). Padrao AFTER_COMMIT + REQUIRES_NEW. Registra valor anterior/novo da alteracao de
 * parametro (trilha de governanca).
 */
@Component
public class GovernancaAuditListener {

    private static final Logger log = LoggerFactory.getLogger(GovernancaAuditListener.class);

    private final AuditLogSegurancaService auditLogService;
    private final ObjectMapper objectMapper;

    public GovernancaAuditListener(AuditLogSegurancaService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAlterarParametro(ParametroOperacionalAlteradoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parametroId", event.parametroId().toString());
        payload.put("chave", event.chave());
        payload.put("versao", event.versao());
        payload.put("valorAnterior", event.valorAnterior());
        payload.put("valorNovo", event.valorNovo());
        auditLogService.gravar(TipoEventoSeguranca.PARAMETRO_OPERACIONAL_ALTERADO, event.atorId(), serializar(payload));
    }

    private String serializar(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Falha ao serializar detalhes de audit de governanca: {}", ex.getMessage());
            return "{\"parametroId\":\"" + payload.getOrDefault("parametroId", "") + "\"}";
        }
    }
}
