package com.dynamis.sep_api.pix.application.listener;

import com.dynamis.sep_api.pix.domain.event.PixTransferenciaConcluidaEvent;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaFalhouEvent;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaSolicitadaEvent;
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
 * Liga os eventos de desembolso Pix (Sprint 20 Task 20.5) ao {@code audit_log_seguranca}.
 *
 * <p>{@link TransactionalEventListener} em {@link TransactionPhase#AFTER_COMMIT} +
 * {@link Propagation#REQUIRES_NEW} (mesmo padrao de {@code CobrancaAuditListener}/{@code
 * OpenFinanceAuditListener}): eventos de transacoes revertidas nao geram trilha e a gravacao abre
 * tx propria na fase pos-commit.
 *
 * <p>CMN 4.656/2018: o desembolso eh operacao financeira sensivel. O {@code usuario_id} aponta para
 * o tomador (sujeito da operacao); o operador que disparou fica registrado em {@code criado_por} da
 * {@code pix_transferencia} (auditoria JPA). Os detalhes carregam apenas ids tecnicos + valor +
 * status — <strong>a chave Pix nunca entra no audit log</strong> (os eventos sequer a transportam).
 */
@Component
public class PixDesembolsoAuditListener {

    private static final Logger log = LoggerFactory.getLogger(PixDesembolsoAuditListener.class);

    private final AuditLogSegurancaService auditLogService;
    private final ObjectMapper objectMapper;

    public PixDesembolsoAuditListener(AuditLogSegurancaService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoSolicitar(PixTransferenciaSolicitadaEvent evento) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("transferenciaId", evento.transferenciaId());
        detalhes.put("contratoId", evento.contratoId());
        detalhes.put("externalId", evento.externalId());
        detalhes.put("valor", evento.valor());
        auditLogService.gravar(
                TipoEventoSeguranca.PIX_TRANSFERENCIA_SOLICITADA, evento.tomadorId(), serializar(detalhes));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoConcluir(PixTransferenciaConcluidaEvent evento) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("transferenciaId", evento.transferenciaId());
        detalhes.put("contratoId", evento.contratoId());
        detalhes.put("externalId", evento.externalId());
        auditLogService.gravar(
                TipoEventoSeguranca.PIX_TRANSFERENCIA_CONCLUIDA, evento.tomadorId(), serializar(detalhes));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoFalhar(PixTransferenciaFalhouEvent evento) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("transferenciaId", evento.transferenciaId());
        detalhes.put("contratoId", evento.contratoId());
        detalhes.put("motivo", evento.motivo());
        auditLogService.gravar(TipoEventoSeguranca.PIX_TRANSFERENCIA_FALHOU, evento.tomadorId(), serializar(detalhes));
    }

    private String serializar(Map<String, Object> detalhes) {
        try {
            return objectMapper.writeValueAsString(detalhes);
        } catch (JsonProcessingException ex) {
            log.warn("falha ao serializar detalhes de auditoria de desembolso Pix", ex);
            return null;
        }
    }
}
