package com.dynamis.sep_api.credito.application.listener;

import com.dynamis.sep_api.credito.domain.event.ParecerRegistradoEvent;
import com.dynamis.sep_api.credito.domain.event.PropostaAprovadaEvent;
import com.dynamis.sep_api.credito.domain.event.PropostaCriadaEvent;
import com.dynamis.sep_api.credito.domain.event.PropostaRejeitadaEvent;
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
 * Liga eventos de dominio do modulo {@code credito} ao {@code audit_log_seguranca} (Sprint 8
 * Task 8.6).
 *
 * <p>{@link TransactionalEventListener} com {@link TransactionPhase#AFTER_COMMIT} garante que
 * eventos de transacoes revertidas nao gerem audit log. Os handlers usam
 * {@link Propagation#REQUIRES_NEW} porque AFTER_COMMIT roda fora da transacao original — sem novo
 * escopo transacional explicito, a gravacao reutilizaria uma transacao ja commitada sem novo
 * flush e perderia o registro. Padrao identico ao {@code OnboardingAuditListener} da Sprint 7.
 *
 * <p>Detalhes JSON sao montados via {@link ObjectMapper} para evitar quebra de JSONB quando
 * valores (justificativa do parecer, motivos) carregarem aspas, barras ou caracteres de
 * controle.
 *
 * <p>LGPD/CMN 4.656/2018: detalhes contem APENAS identificadores tecnicos + score + decisao +
 * justificativa truncada. Dados sensiveis (CPF, payload bruto de regras) NUNCA entram aqui — a
 * trilha completa de regras avaliadas vive em {@code regra_credito_avaliada}.
 */
@Component
public class CreditoAuditListener {

    private static final Logger log = LoggerFactory.getLogger(CreditoAuditListener.class);

    /** Limite seguro para justificativa em audit log — evita JSONB gigante. */
    private static final int JUSTIFICATIVA_MAX_AUDIT = 200;

    private final AuditLogSegurancaService auditLogService;
    private final ObjectMapper objectMapper;

    public CreditoAuditListener(AuditLogSegurancaService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCriar(PropostaCriadaEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propostaId", event.propostaId().toString());
        payload.put("solicitacaoOnboardingId", event.solicitacaoOnboardingId().toString());
        auditLogService.gravar(TipoEventoSeguranca.PROPOSTA_CRIADA, event.tomadorId(), serializar(payload));
    }

    // PROPOSTA_AVALIADA_MOTOR e gravada SINCRONA em PropostaAvaliacaoTransacional.avaliar() —
    // Step 008.6.3 exige garantia "motor sem audit -> PENDENCIA". Nao ha handler AFTER_COMMIT
    // para esse tipo.

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRegistrarParecer(ParecerRegistradoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propostaId", event.propostaId().toString());
        payload.put("parecerId", event.parecerId().toString());
        payload.put("decisao", event.decisao().name());
        payload.put("justificativa", truncar(event.justificativa()));
        payload.put("scoreMotorSnapshot", event.scoreMotor());
        auditLogService.gravar(TipoEventoSeguranca.PARECER_REGISTRADO, event.pareceristaId(), serializar(payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAprovar(PropostaAprovadaEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propostaId", event.propostaId().toString());
        payload.put("parecerId", event.parecerId().toString());
        auditLogService.gravar(TipoEventoSeguranca.PROPOSTA_APROVADA, event.tomadorId(), serializar(payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRejeitar(PropostaRejeitadaEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propostaId", event.propostaId().toString());
        payload.put("origem", event.origem().name());
        if (event.parecerId() != null) {
            payload.put("parecerId", event.parecerId().toString());
        }
        auditLogService.gravar(TipoEventoSeguranca.PROPOSTA_REJEITADA, event.tomadorId(), serializar(payload));
    }

    private String truncar(String justificativa) {
        if (justificativa == null) {
            return "NAO_INFORMADA";
        }
        if (justificativa.length() <= JUSTIFICATIVA_MAX_AUDIT) {
            return justificativa;
        }
        return justificativa.substring(0, JUSTIFICATIVA_MAX_AUDIT) + "...";
    }

    private String serializar(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Falha ao serializar detalhes de audit log de credito: {}", ex.getMessage());
            return "{}";
        }
    }
}
