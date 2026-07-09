package com.dynamis.sep_api.credores.application.listener;

import com.dynamis.sep_api.credores.domain.event.AporteCredoraRegistradoEvent;
import com.dynamis.sep_api.credores.domain.event.EmpresaCredoraCadastradaEvent;
import com.dynamis.sep_api.credores.domain.event.EmpresaCredoraElegibilidadeDefinidaEvent;
import com.dynamis.sep_api.credores.domain.event.InteresseCredoraCanceladoEvent;
import com.dynamis.sep_api.credores.domain.event.InteresseCredoraRegistradoEvent;
import com.dynamis.sep_api.credores.domain.event.OperacaoFinanciadaAssociadaEvent;
import com.dynamis.sep_api.credores.domain.vo.StatusElegibilidade;
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
 * Liga eventos de dominio do modulo {@code credores} ao {@code audit_log_seguranca} (Sprint 16).
 *
 * <p>{@link TransactionalEventListener} com {@link TransactionPhase#AFTER_COMMIT} +
 * {@link Propagation#REQUIRES_NEW} garante que so cadastros efetivamente commitados gerem trilha,
 * e que a gravacao ocorra em nova transacao (AFTER_COMMIT roda fora da original).
 *
 * <p>LGPD/CMN 4.656/2018: detalhes carregam apenas identificadores tecnicos e CNPJ mascarado.
 */
@Component
public class CredoresAuditListener {

    private static final Logger log = LoggerFactory.getLogger(CredoresAuditListener.class);

    private final AuditLogSegurancaService auditLogService;
    private final ObjectMapper objectMapper;

    public CredoresAuditListener(AuditLogSegurancaService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCadastrar(EmpresaCredoraCadastradaEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("empresaCredoraId", event.empresaCredoraId().toString());
        payload.put("cnpjMascarado", mascararDocumento(event.cnpj()));
        auditLogService.gravar(TipoEventoSeguranca.CREDORA_CADASTRADA, event.usuarioId(), serializar(payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoDefinirElegibilidade(EmpresaCredoraElegibilidadeDefinidaEvent event) {
        TipoEventoSeguranca tipo = event.elegibilidade() == StatusElegibilidade.ELEGIVEL
                ? TipoEventoSeguranca.CREDORA_ELEGIVEL
                : TipoEventoSeguranca.CREDORA_INELEGIVEL;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("empresaCredoraId", event.empresaCredoraId().toString());
        payload.put("elegibilidade", event.elegibilidade().name());
        if (event.motivo() != null) {
            payload.put("motivo", event.motivo());
        }
        auditLogService.gravar(tipo, event.usuarioId(), serializar(payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRegistrarInteresse(InteresseCredoraRegistradoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("interesseId", event.interesseId().toString());
        payload.put("empresaCredoraId", event.empresaCredoraId().toString());
        payload.put("oportunidadeId", event.oportunidadeId().toString());
        auditLogService.gravar(
                TipoEventoSeguranca.CREDORA_INTERESSE_REGISTRADO, event.usuarioId(), serializar(payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCancelarInteresse(InteresseCredoraCanceladoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("interesseId", event.interesseId().toString());
        payload.put("empresaCredoraId", event.empresaCredoraId().toString());
        payload.put("oportunidadeId", event.oportunidadeId().toString());
        auditLogService.gravar(TipoEventoSeguranca.CREDORA_INTERESSE_CANCELADO, event.usuarioId(), serializar(payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAssociarOperacao(OperacaoFinanciadaAssociadaEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operacaoId", event.operacaoId().toString());
        payload.put("empresaCredoraId", event.empresaCredoraId().toString());
        payload.put("contratoId", event.contratoId().toString());
        auditLogService.gravar(TipoEventoSeguranca.CREDORA_OPERACAO_ASSOCIADA, event.usuarioId(), serializar(payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRegistrarAporte(AporteCredoraRegistradoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("aporteId", event.aporteId().toString());
        payload.put("operacaoId", event.operacaoId().toString());
        payload.put("empresaCredoraId", event.empresaCredoraId().toString());
        payload.put("valor", event.valor().toPlainString());
        auditLogService.gravar(TipoEventoSeguranca.CREDORA_APORTE_REGISTRADO, event.usuarioId(), serializar(payload));
    }

    private String serializar(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Falha ao serializar detalhes de audit credores; usando fallback minimo");
            return "{\"empresaCredoraId\":\"" + payload.getOrDefault("empresaCredoraId", "") + "\"}";
        }
    }

    /** Mantem primeiros 3 + ultimos 2 digitos; mascara os restantes. */
    private static String mascararDocumento(String documento) {
        if (documento == null || documento.length() < 6) return "***";
        int len = documento.length();
        return documento.substring(0, 3) + "*".repeat(len - 5) + documento.substring(len - 2);
    }
}
