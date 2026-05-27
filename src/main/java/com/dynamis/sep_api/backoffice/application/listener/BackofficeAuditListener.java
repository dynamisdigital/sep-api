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
        gravarSeguro(TipoEventoSeguranca.ITEM_FILA_CRIADO, null, event.itemId(), () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("itemId", event.itemId().toString());
            payload.put("tipo", event.tipo().name());
            payload.put("prioridade", event.prioridade().name());
            payload.put("tipoEntidade", event.tipoEntidade().name());
            payload.put("entidadeId", event.entidadeId().toString());
            return payload;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAssumirItem(ItemAssumidoEvent event) {
        gravarSeguro(TipoEventoSeguranca.ITEM_ASSUMIDO, event.atribuidoA(), event.itemId(), () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("itemId", event.itemId().toString());
            payload.put("atribuidoEm", event.atribuidoEm().toString());
            return payload;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRegistrarComentario(ComentarioRegistradoEvent event) {
        gravarSeguro(TipoEventoSeguranca.COMENTARIO_REGISTRADO, event.autorId(), event.itemId(), () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("itemId", event.itemId().toString());
            payload.put("comentarioId", event.comentarioId().toString());
            payload.put("conteudoResumido", truncar(event.conteudoResumido()));
            return payload;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoResolverItem(ItemResolvidoEvent event) {
        gravarSeguro(TipoEventoSeguranca.ITEM_RESOLVIDO, event.resolvidoPor(), event.itemId(), () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("itemId", event.itemId().toString());
            payload.put("justificativaResumida", truncar(event.justificativaResumida()));
            return payload;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoIgnorarItem(ItemIgnoradoEvent event) {
        gravarSeguro(TipoEventoSeguranca.ITEM_IGNORADO, event.ignoradoPor(), event.itemId(), () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("itemId", event.itemId().toString());
            payload.put("justificativaResumida", truncar(event.justificativaResumida()));
            return payload;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoDispararReprocesso(ReprocessoDisparadoEvent event) {
        gravarSeguro(TipoEventoSeguranca.REPROCESSO_DISPARADO, event.disparadoPor(), event.reprocessoId(), () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reprocessoId", event.reprocessoId().toString());
            payload.put("tipo", event.tipo().name());
            payload.put("status", event.status() != null ? event.status().name() : null);
            payload.put("identificadorExterno", event.identificadorExterno());
            if (event.tipoChamada() != null) {
                payload.put("tipoChamada", event.tipoChamada().name());
            }
            if (event.itemId() != null) {
                payload.put("itemId", event.itemId().toString());
            }
            return payload;
        });
    }

    /**
     * Wrapper que garante que falha de audit (serializacao, DB indisponivel, constraint) nao
     * propague pra fora do listener — fix review manual Task 14.8 — "Falha de audit nao quebra
     * fluxo principal".
     */
    private void gravarSeguro(
            TipoEventoSeguranca tipo,
            UUID usuarioId,
            UUID contexto,
            java.util.function.Supplier<Map<String, Object>> payloadSupplier) {
        try {
            String detalhes = serializar(payloadSupplier.get(), contexto);
            auditLogService.gravar(tipo, usuarioId, detalhes);
        } catch (RuntimeException ex) {
            LOG.error("Falha ao gravar audit {} contexto={}; flow principal preservado", tipo, contexto, ex);
        }
    }

    /**
     * Sanitiza texto livre antes do audit (fix review manual Task 14.8): mascara CPF e CNPJ que
     * o operador possa ter digitado em comentario/justificativa, depois trunca em 80 chars.
     * Use cases ja truncam upstream — guard defensivo em camada extra.
     */
    private static String truncar(String texto) {
        if (texto == null) {
            return null;
        }
        String mascarado = mascararDocumentos(texto);
        if (mascarado.length() <= MAX_RESUMO_AUDIT) {
            return mascarado;
        }
        return mascarado.substring(0, MAX_RESUMO_AUDIT) + "...";
    }

    private static String mascararDocumentos(String texto) {
        // CPF: 11 digitos (com/sem pontuacao) -> ***.***.***-**
        String semCpf = texto.replaceAll("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b", "***.***.***-**");
        // CNPJ: 14 digitos (com/sem pontuacao) -> **.***.***/****-**
        return semCpf.replaceAll("\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b", "**.***.***/****-**");
    }

    private static final int MAX_RESUMO_AUDIT = 80;

    private String serializar(Map<String, Object> payload, UUID contexto) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            LOG.warn("Falha ao serializar audit do backoffice contexto={}", contexto, ex);
            return "{}";
        }
    }
}
