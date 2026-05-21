package com.dynamis.sep_api.contratos.application.listener;

import com.dynamis.sep_api.contratos.domain.event.AssinaturaEnviadaEvent;
import com.dynamis.sep_api.contratos.domain.event.AssinaturaVisualizadaEvent;
import com.dynamis.sep_api.contratos.domain.event.CcbGeradaEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoAceitoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoAssinadoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoCanceladoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoGeradoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoNovaVersaoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoRecusadoEvent;
import com.dynamis.sep_api.contratos.domain.event.DocumentoAssinadoBaixadoEvent;
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
 * Liga eventos de dominio do modulo {@code contratos} ao {@code audit_log_seguranca} (Sprint 10
 * Task 10.7).
 *
 * <p>{@link TransactionalEventListener} com {@link TransactionPhase#AFTER_COMMIT} garante que
 * eventos de transacoes revertidas nao gerem audit log. Os handlers usam
 * {@link Propagation#REQUIRES_NEW} porque AFTER_COMMIT roda fora da transacao original — sem novo
 * escopo transacional explicito, a gravacao reutilizaria uma transacao ja commitada sem novo
 * flush e perderia o registro. Padrao identico ao {@code OnboardingAuditListener} da Sprint 7,
 * {@code CreditoAuditListener} da Sprint 8 e {@code OpenFinanceAuditListener} da Sprint 9.
 *
 * <p>Detalhes JSON sao montados via {@link ObjectMapper} para evitar quebra de JSONB quando
 * valores (justificativa, user-agent) carregarem aspas, barras ou caracteres de controle.
 *
 * <p>CMN 4.656/2018 Art. 11 + LGPD: contratos sao documentos legais com retencao minima de 10
 * anos. Detalhes contem APENAS identificadores tecnicos + hash + ip/user-agent truncados +
 * justificativa truncada. Conteudo integral do contrato e clausulas NUNCA entram no audit log —
 * trilha completa vive em {@code versao_contrato} e {@code clausula_contratual}.
 */
@Component
public class ContratoAuditListener {

    private static final Logger log = LoggerFactory.getLogger(ContratoAuditListener.class);

    /** Limite seguro para campos livres em audit log — evita JSONB gigante. */
    private static final int CAMPO_LIVRE_MAX_AUDIT = 200;

    private final AuditLogSegurancaService auditLogService;
    private final ObjectMapper objectMapper;

    public ContratoAuditListener(AuditLogSegurancaService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoGerar(ContratoGeradoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contratoId", event.contratoId().toString());
        payload.put("propostaId", event.propostaId().toString());
        payload.put("versaoId", event.versaoId().toString());
        payload.put("numeroVersao", event.numeroVersao());
        payload.put("hashSha256", event.hashSha256());
        auditLogService.gravar(
                TipoEventoSeguranca.CONTRATO_GERADO,
                event.tomadorId(),
                serializar(payload, event.contratoId().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCriarNovaVersao(ContratoNovaVersaoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contratoId", event.contratoId().toString());
        payload.put("propostaId", event.propostaId().toString());
        payload.put("versaoId", event.versaoId().toString());
        payload.put("numeroVersao", event.numeroVersao());
        payload.put("hashSha256", event.hashSha256());
        auditLogService.gravar(
                TipoEventoSeguranca.CONTRATO_NOVA_VERSAO,
                event.tomadorId(),
                serializar(payload, event.contratoId().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAceitar(ContratoAceitoEvent event) {
        // IP e user-agent vivem APENAS nas colunas dedicadas de audit_log_seguranca; nao
        // duplicamos no JSONB pra manter consistencia forense (uma fonte por campo) e evitar
        // discrepancia de truncamento entre coluna (45/500 chars) e payload.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contratoId", event.contratoId().toString());
        payload.put("propostaId", event.propostaId().toString());
        payload.put("versaoId", event.versaoId().toString());
        payload.put("numeroVersao", event.numeroVersao());
        payload.put("hashSha256", event.hashSha256());
        auditLogService.gravar(
                TipoEventoSeguranca.CONTRATO_ACEITO,
                event.tomadorId(),
                event.ipOrigem(),
                event.userAgentOrigem(),
                serializar(payload, event.contratoId().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCancelar(ContratoCanceladoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contratoId", event.contratoId().toString());
        payload.put("propostaId", event.propostaId().toString());
        payload.put("tomadorId", event.tomadorId().toString());
        payload.put("justificativa", truncar(event.justificativa()));
        auditLogService.gravar(
                TipoEventoSeguranca.CONTRATO_CANCELADO,
                event.canceladoPorId(),
                serializar(payload, event.contratoId().toString()));
    }

    // ============== Sprint 11 Task 11.8: ciclo de assinatura digital ==============

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoGerarCcb(CcbGeradaEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contratoId", event.contratoId().toString());
        payload.put("propostaId", event.propostaId().toString());
        payload.put("versaoId", event.versaoId().toString());
        payload.put("numeroVersao", event.numeroVersao());
        payload.put("hashPdfGerado", event.hashPdfGerado());
        auditLogService.gravar(
                TipoEventoSeguranca.CCB_GERADA,
                event.tomadorId(),
                serializar(payload, event.contratoId().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoEnviarAssinatura(AssinaturaEnviadaEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contratoId", event.contratoId().toString());
        payload.put("propostaId", event.propostaId().toString());
        payload.put("versaoId", event.versaoId().toString());
        payload.put("envelopeId", event.envelopeId().toString());
        payload.put("idEnvelopeExterno", event.idEnvelopeExterno());
        payload.put("provider", event.provider());
        payload.put("hashPdfEnviado", event.hashPdfEnviado());
        auditLogService.gravar(
                TipoEventoSeguranca.ASSINATURA_ENVIADA,
                event.tomadorId(),
                serializar(payload, event.contratoId().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoVisualizarAssinatura(AssinaturaVisualizadaEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contratoId", event.contratoId().toString());
        payload.put("envelopeId", event.envelopeId().toString());
        payload.put("provider", event.provider());
        payload.put(
                "dataEvento",
                event.dataEvento() == null ? null : event.dataEvento().toString());
        auditLogService.gravar(
                TipoEventoSeguranca.ASSINATURA_VISUALIZADA,
                event.tomadorId(),
                serializar(payload, event.contratoId().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAssinar(ContratoAssinadoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contratoId", event.contratoId().toString());
        payload.put("propostaId", event.propostaId().toString());
        payload.put("versaoId", event.versaoId().toString());
        payload.put("envelopeId", event.envelopeId().toString());
        payload.put("documentoAssinadoId", event.documentoAssinadoId().toString());
        payload.put("hashPdfAssinado", event.hashPdfAssinado());
        // Spec §11.8: timestamp do provider eh exigencia regulatoria pra ASSINATURA_ASSINADA —
        // momento real da assinatura, nao quando o callback chegou no SEP.
        payload.put(
                "dataAssinatura",
                event.dataAssinatura() == null ? null : event.dataAssinatura().toString());
        auditLogService.gravar(
                TipoEventoSeguranca.ASSINATURA_ASSINADA,
                event.tomadorId(),
                serializar(payload, event.contratoId().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRecusar(ContratoRecusadoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contratoId", event.contratoId().toString());
        payload.put("propostaId", event.propostaId().toString());
        payload.put("versaoId", event.versaoId().toString());
        payload.put("envelopeId", event.envelopeId().toString());
        auditLogService.gravar(
                TipoEventoSeguranca.ASSINATURA_RECUSADA,
                event.tomadorId(),
                serializar(payload, event.contratoId().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoBaixarDocumentoAssinado(DocumentoAssinadoBaixadoEvent event) {
        // ip + user-agent vivem APENAS nas colunas dedicadas (LGPD). JSONB carrega so IDs tecnicos.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contratoId", event.contratoId().toString());
        payload.put("envelopeId", event.envelopeId().toString());
        payload.put("documentoAssinadoId", event.documentoAssinadoId().toString());
        auditLogService.gravar(
                TipoEventoSeguranca.DOCUMENTO_ASSINADO_BAIXADO,
                event.baixadoPorId(),
                event.ipOrigem(),
                event.userAgentOrigem(),
                serializar(payload, event.contratoId().toString()));
    }

    /**
     * Serializa o payload em JSON; em caso de falha, devolve fallback minimo com o
     * {@code contratoId} pra preservar rastreabilidade no audit log (em vez de {@code "{}"}, que
     * mascararia a falha como payload genuinamente vazio). Auditoria nao pode quebrar o fluxo de
     * negocio (mesma decisao do {@code OnboardingAuditListener} da Sprint 6).
     */
    private String serializar(Map<String, Object> payload, String contratoId) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Falha ao serializar payload de audit contratos; usando fallback minimo: {}", e.getMessage());
            return "{\"contratoId\":\"" + contratoId + "\",\"erroSerializacao\":true}";
        }
    }

    private static String truncar(String valor) {
        if (valor == null) {
            return null;
        }
        return valor.length() > CAMPO_LIVRE_MAX_AUDIT ? valor.substring(0, CAMPO_LIVRE_MAX_AUDIT) : valor;
    }
}
