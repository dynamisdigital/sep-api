package com.dynamis.sep_api.credito.application.listener;

import com.dynamis.sep_api.credito.domain.event.OpenFinanceAutorizadoEvent;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceConsentimentoIniciadoEvent;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceDadosRecebidosEvent;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceNegadoEvent;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceReavaliacaoEvent;
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
 * Liga eventos de dominio Open Finance ao {@code audit_log_seguranca} (Sprint 9 Task 9.7).
 *
 * <p>Padrao identico ao {@code CreditoAuditListener} (Sprint 8 Task 8.6):
 * {@link TransactionalEventListener} {@code AFTER_COMMIT} + {@code REQUIRES_NEW} pra garantir
 * que eventos de transacao revertida nao gerem audit log e que cada gravacao tenha tx propria.
 *
 * <p>LGPD: detalhes JSON carregam APENAS identificadores tecnicos + agregados (numeroMesesAvaliados,
 * scoreAnterior/Novo, statusAnterior/Novo). NUNCA payload bruto de movimentacao bancaria ou dados
 * pessoais — o snapshot consolidado vive em {@code movimentacao_open_finance.payload_consolidado}
 * (ja sanitizado).
 */
@Component
public class OpenFinanceAuditListener {

    private static final Logger log = LoggerFactory.getLogger(OpenFinanceAuditListener.class);

    private final AuditLogSegurancaService auditLogService;
    private final ObjectMapper objectMapper;

    public OpenFinanceAuditListener(AuditLogSegurancaService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoIniciar(OpenFinanceConsentimentoIniciadoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("consentimentoId", event.consentimentoId().toString());
        payload.put("propostaId", event.propostaId().toString());
        payload.put("idExternoCelcoin", event.idExternoCelcoin());
        auditLogService.gravar(
                TipoEventoSeguranca.OPEN_FINANCE_CONSENTIMENTO_INICIADO, event.tomadorId(), serializar(payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAutorizar(OpenFinanceAutorizadoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("consentimentoId", event.consentimentoId().toString());
        payload.put("propostaId", event.propostaId().toString());
        payload.put("idExternoCelcoin", event.idExternoCelcoin());
        auditLogService.gravar(TipoEventoSeguranca.OPEN_FINANCE_AUTORIZADO, event.tomadorId(), serializar(payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoNegar(OpenFinanceNegadoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("consentimentoId", event.consentimentoId().toString());
        payload.put("propostaId", event.propostaId().toString());
        auditLogService.gravar(TipoEventoSeguranca.OPEN_FINANCE_NEGADO, event.tomadorId(), serializar(payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRecebimentoDeDados(OpenFinanceDadosRecebidosEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("movimentacaoId", event.movimentacaoId().toString());
        payload.put("consentimentoId", event.consentimentoId().toString());
        payload.put("propostaId", event.propostaId().toString());
        payload.put("numeroMesesAvaliados", event.numeroMesesAvaliados());
        auditLogService.gravar(
                TipoEventoSeguranca.OPEN_FINANCE_DADOS_RECEBIDOS, event.tomadorId(), serializar(payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoReavaliar(OpenFinanceReavaliacaoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propostaId", event.propostaId().toString());
        payload.put("consentimentoId", event.consentimentoId().toString());
        payload.put("scoreAnterior", event.scoreAnterior());
        payload.put("scoreNovo", event.scoreNovo());
        payload.put("statusAnterior", event.statusAnterior().name());
        payload.put("statusNovo", event.statusNovo().name());
        auditLogService.gravar(TipoEventoSeguranca.OPEN_FINANCE_REAVALIACAO, event.tomadorId(), serializar(payload));
    }

    private String serializar(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Falha ao serializar detalhes de audit log Open Finance: {}", ex.getMessage());
            return "{}";
        }
    }
}
