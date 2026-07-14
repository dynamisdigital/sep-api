package com.dynamis.sep_api.pix.application.listener;

import com.dynamis.sep_api.pix.domain.event.PixChaveCadastradaEvent;
import com.dynamis.sep_api.pix.domain.event.PixChaveRemovidaEvent;
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
 * Liga os eventos de gestao de chave Pix (Sprint 31) ao {@code audit_log_seguranca}.
 *
 * <p>{@link TransactionalEventListener} em {@link TransactionPhase#AFTER_COMMIT} +
 * {@link Propagation#REQUIRES_NEW}, mesmo padrao de {@code PixDesembolsoAuditListener}: eventos de
 * transacoes revertidas nao geram trilha e a gravacao abre transacao propria pos-commit.
 *
 * <p>CMN 4.656/2018 + LGPD: {@code usuario_id} e o operador (financeiro/admin) que executou a
 * mutacao. Os detalhes levam apenas {@code chaveId}, {@code tipo}, {@code status} e a conta escrow
 * local — <strong>nunca</strong> valor, hash, mascara, provider id ou idempotency key (os eventos
 * sequer os transportam). Falha de serializacao loga apenas o aviso, sem despejar o evento.
 */
@Component
public class PixChaveAuditListener {

    private static final Logger log = LoggerFactory.getLogger(PixChaveAuditListener.class);

    private final AuditLogSegurancaService auditLogService;
    private final ObjectMapper objectMapper;

    public PixChaveAuditListener(AuditLogSegurancaService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCadastrar(PixChaveCadastradaEvent evento) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("chaveId", evento.chaveId());
        detalhes.put("contaEscrowId", evento.contaEscrowId());
        detalhes.put("tipo", evento.tipo());
        detalhes.put("status", "ATIVA");
        auditLogService.gravar(TipoEventoSeguranca.PIX_CHAVE_CADASTRADA, evento.operadorId(), serializar(detalhes));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRemover(PixChaveRemovidaEvent evento) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("chaveId", evento.chaveId());
        detalhes.put("contaEscrowId", evento.contaEscrowId());
        detalhes.put("tipo", evento.tipo());
        detalhes.put("status", "INATIVA");
        auditLogService.gravar(TipoEventoSeguranca.PIX_CHAVE_REMOVIDA, evento.operadorId(), serializar(detalhes));
    }

    private String serializar(Map<String, Object> detalhes) {
        try {
            return objectMapper.writeValueAsString(detalhes);
        } catch (JsonProcessingException ex) {
            log.warn("falha ao serializar detalhes de auditoria de chave Pix", ex);
            return null;
        }
    }
}
