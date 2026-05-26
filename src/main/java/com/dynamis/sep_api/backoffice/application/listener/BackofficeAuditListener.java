package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.domain.event.ComentarioRegistradoEvent;
import com.dynamis.sep_api.backoffice.domain.event.ItemAssumidoEvent;
import com.dynamis.sep_api.backoffice.domain.event.ItemFilaCriadoEvent;
import com.dynamis.sep_api.backoffice.domain.event.ItemIgnoradoEvent;
import com.dynamis.sep_api.backoffice.domain.event.ItemResolvidoEvent;
import com.dynamis.sep_api.backoffice.domain.event.ReprocessoDisparadoEvent;
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
import java.util.UUID;

/**
 * Liga eventos de dominio do modulo {@code backoffice} ao {@code audit_log_seguranca} (Sprint 14
 * Task 14.8).
 *
 * <p>{@link TransactionalEventListener} com {@link TransactionPhase#AFTER_COMMIT} + {@code
 * REQUIRES_NEW} alinha com {@code CobrancaAuditListener}/{@code ContratoAuditListener}.
 *
 * <p>Sanitizacao: payload JSON carrega apenas identificadores tecnicos, status e
 * {@code conteudoResumido}/{@code justificativaResumida} (max 80 chars vindos do evento). Sem
 * CPF/CNPJ, telefone, dados bancarios, token de step-up ou payload bruto.
 */
@Component
public class BackofficeAuditListener {

    private static final Logger LOG = LoggerFactory.getLogger(BackofficeAuditListener.class);

    private final AuditLogSegurancaService auditLogService;
    private final ObjectMapper objectMapper;

    public BackofficeAuditListener(AuditLogSegurancaService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCriarItem(ItemFilaCriadoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemId", event.itemId().toString());
        payload.put("tipo", event.tipo().name());
        payload.put("prioridade", event.prioridade().name());
        payload.put("tipoEntidade", event.tipoEntidade().name());
        payload.put("entidadeId", event.entidadeId().toString());
        auditLogService.gravar(TipoEventoSeguranca.ITEM_FILA_CRIADO, null, serializar(payload, event.itemId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAssumirItem(ItemAssumidoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemId", event.itemId().toString());
        payload.put("atribuidoEm", event.atribuidoEm().toString());
        auditLogService.gravar(TipoEventoSeguranca.ITEM_ASSUMIDO, event.atribuidoA(), serializar(payload, event.itemId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRegistrarComentario(ComentarioRegistradoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemId", event.itemId().toString());
        payload.put("comentarioId", event.comentarioId().toString());
        payload.put("conteudoResumido", event.conteudoResumido());
        auditLogService.gravar(
                TipoEventoSeguranca.COMENTARIO_REGISTRADO, event.autorId(), serializar(payload, event.itemId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoResolverItem(ItemResolvidoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemId", event.itemId().toString());
        payload.put("justificativaResumida", event.justificativaResumida());
        auditLogService.gravar(
                TipoEventoSeguranca.ITEM_RESOLVIDO, event.resolvidoPor(), serializar(payload, event.itemId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoIgnorarItem(ItemIgnoradoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemId", event.itemId().toString());
        payload.put("justificativaResumida", event.justificativaResumida());
        auditLogService.gravar(
                TipoEventoSeguranca.ITEM_IGNORADO, event.ignoradoPor(), serializar(payload, event.itemId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoDispararReprocesso(ReprocessoDisparadoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reprocessoId", event.reprocessoId().toString());
        payload.put("tipo", event.tipo().name());
        payload.put("identificadorExterno", event.identificadorExterno());
        auditLogService.gravar(
                TipoEventoSeguranca.REPROCESSO_DISPARADO,
                event.disparadoPor(),
                serializar(payload, event.reprocessoId()));
    }

    private String serializar(Map<String, Object> payload, UUID contexto) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            LOG.warn("Falha ao serializar audit do backoffice contexto={}", contexto, ex);
            return "{}";
        }
    }
}
