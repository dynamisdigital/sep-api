package com.dynamis.sep_api.cobranca.application.listener;

import com.dynamis.sep_api.cobranca.domain.event.AgendaGeradaEvent;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaAtrasouEvent;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaPagaEvent;
import com.dynamis.sep_api.cobranca.domain.event.RecebimentoRegistradoEvent;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.AgendaPagamentoRepository;
import com.dynamis.sep_api.escrow.domain.event.MovimentacaoEscrowCriadaEvent;
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
 * Liga eventos de dominio do modulo {@code cobranca} (e movimentacao escrow disparada por
 * recebimento) ao {@code audit_log_seguranca} (Sprint 12 Task 12.7).
 *
 * <p>{@link TransactionalEventListener} com {@link TransactionPhase#AFTER_COMMIT} garante que
 * eventos de transacoes revertidas nao gerem audit log. Handlers usam
 * {@link Propagation#REQUIRES_NEW} pra abrir tx propria na fase pos-commit, alinhado a
 * {@code ContratoAuditListener} (Sprint 10) e {@code CreditoAuditListener} (Sprint 8).
 *
 * <p>CMN 4.656/2018 Art. 9 + Art. 11 + segregacao patrimonial: cobranca e movimentacao escrow
 * formam trilha financeira auditavel. Detalhes JSON carregam APENAS identificadores tecnicos +
 * valor + status + datas. Dados bancarios (conta, agencia), documento pessoal do tomador e
 * payload bruto de provider futuro NAO entram no audit log.
 */
@Component
public class CobrancaAuditListener {

    private static final Logger log = LoggerFactory.getLogger(CobrancaAuditListener.class);

    private final AuditLogSegurancaService auditLogService;
    private final AgendaPagamentoRepository agendaRepository;
    private final ObjectMapper objectMapper;

    public CobrancaAuditListener(
            AuditLogSegurancaService auditLogService,
            AgendaPagamentoRepository agendaRepository,
            ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.agendaRepository = agendaRepository;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoGerarAgenda(AgendaGeradaEvent event) {
        Map<String, Object> agendaPayload = new LinkedHashMap<>();
        agendaPayload.put("agendaId", event.agendaId().toString());
        agendaPayload.put("contratoId", event.contratoId().toString());
        agendaPayload.put("propostaId", event.propostaId().toString());
        agendaPayload.put("numeroParcelas", event.numeroParcelas());
        agendaPayload.put("valorTotal", event.valorTotal().toPlainString());
        agendaPayload.put("dataGeracao", event.dataGeracao().toString());
        auditLogService.gravar(
                TipoEventoSeguranca.AGENDA_GERADA, event.tomadorId(), serializar(agendaPayload, event.agendaId()));

        agendaRepository.findById(event.agendaId()).ifPresent(agenda -> registrarParcelasCriadas(agenda, event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRegistrarRecebimento(RecebimentoRegistradoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recebimentoId", event.recebimentoId().toString());
        payload.put("parcelaId", event.parcelaId().toString());
        payload.put("agendaId", event.agendaId().toString());
        payload.put("contratoId", event.contratoId().toString());
        payload.put("valorRecebido", event.valorRecebido().toPlainString());
        payload.put("meioPagamento", event.meioPagamento());
        payload.put("dataRecebimento", event.dataRecebimento().toString());
        auditLogService.gravar(
                TipoEventoSeguranca.RECEBIMENTO_REGISTRADO,
                event.registradoPor(),
                serializar(payload, event.recebimentoId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoPagarParcela(ParcelaPagaEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parcelaId", event.parcelaId().toString());
        payload.put("agendaId", event.agendaId().toString());
        payload.put("contratoId", event.contratoId().toString());
        payload.put("numero", event.numero());
        payload.put("totalRecebido", event.totalRecebido().toPlainString());
        auditLogService.gravar(TipoEventoSeguranca.PARCELA_PAGA, null, serializar(payload, event.parcelaId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAtrasarParcela(ParcelaAtrasouEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parcelaId", event.parcelaId().toString());
        payload.put("agendaId", event.agendaId().toString());
        payload.put("contratoId", event.contratoId().toString());
        payload.put("numero", event.numero());
        payload.put("dataVencimento", event.dataVencimento().toString());
        auditLogService.gravar(TipoEventoSeguranca.PARCELA_ATRASADA, null, serializar(payload, event.parcelaId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCriarMovimentacaoEscrow(MovimentacaoEscrowCriadaEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("movimentacaoId", event.movimentacaoId().toString());
        payload.put("walletId", event.walletId().toString());
        payload.put("propostaId", event.propostaId().toString());
        payload.put("tipo", event.tipo());
        payload.put("valor", event.valor().toPlainString());
        payload.put("dataMovimentacao", event.dataMovimentacao().toString());
        if (event.externalReferenceId() != null) {
            payload.put("externalReferenceId", event.externalReferenceId().toString());
        }
        auditLogService.gravar(
                TipoEventoSeguranca.MOVIMENTACAO_ESCROW_CRIADA, null, serializar(payload, event.movimentacaoId()));
    }

    private void registrarParcelasCriadas(AgendaPagamento agenda, AgendaGeradaEvent event) {
        for (ParcelaCobranca parcela : agenda.getParcelas()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("parcelaId", parcela.getId().toString());
            payload.put("agendaId", event.agendaId().toString());
            payload.put("contratoId", event.contratoId().toString());
            payload.put("numero", parcela.getNumero());
            payload.put("dataVencimento", parcela.getDataVencimento().toString());
            payload.put("valor", parcela.valorTotal().toPlainString());
            auditLogService.gravar(
                    TipoEventoSeguranca.PARCELA_CRIADA, event.tomadorId(), serializar(payload, parcela.getId()));
        }
    }

    private String serializar(Map<String, Object> payload, Object correlationKey) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Falha serializando payload de audit log para {}: {}", correlationKey, ex.getMessage());
            return "{\"_audit_serialization_error\":true}";
        }
    }
}
