package com.dynamis.sep_api.onboarding.application.listener;

import com.dynamis.sep_api.onboarding.domain.event.DocumentoCadastralEnviadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingIniciadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.VerificacaoKycDisparadaEvent;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
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
 * Liga eventos de dominio do modulo {@code onboarding} ao {@code audit_log_seguranca} (Sprint 5).
 *
 * <p>{@link TransactionalEventListener} com {@link TransactionPhase#AFTER_COMMIT} garante que
 * eventos de transacoes revertidas nao gerem audit log. Os handlers sao anotados com
 * {@link Transactional}({@link Propagation#REQUIRES_NEW}) porque AFTER_COMMIT roda fora da
 * transacao original — sem novo escopo transacional explicito, a gravacao pode reutilizar uma
 * transacao ja commitada sem novo flush e perder o registro.
 *
 * <p>Detalhes JSON sao montados via {@link ObjectMapper} para evitar quebra de JSONB quando
 * valores vindos do provider externo (ex.: {@code idVerificacaoExterna}) carregarem aspas,
 * barras ou caracteres de controle.
 *
 * <p>LGPD/CMN 4.656/2018: detalhes contem APENAS identificadores tecnicos e metadados
 * sanitizados. CPF completo, nome e payload bruto NUNCA entram aqui — payload bruto vive em
 * {@code resultado_verificacao.payload_provider}.
 */
@Component
public class OnboardingAuditListener {

    private static final Logger log = LoggerFactory.getLogger(OnboardingAuditListener.class);

    private final AuditLogSegurancaService auditLogService;
    private final ObjectMapper objectMapper;

    public OnboardingAuditListener(AuditLogSegurancaService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoIniciar(OnboardingIniciadoEvent event) {
        String detalhes =
                serializar(Map.of("solicitacaoId", event.solicitacaoId().toString()));
        auditLogService.gravar(TipoEventoSeguranca.KYC_INICIADO, event.usuarioId(), detalhes);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoEnviarDocumento(DocumentoCadastralEnviadoEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("solicitacaoId", event.solicitacaoId().toString());
        payload.put("documentoId", event.documentoId().toString());
        payload.put("tipoDocumento", event.tipo().name());
        payload.put("sha256", event.sha256());
        auditLogService.gravar(TipoEventoSeguranca.KYC_DOCUMENTO_ENVIADO, event.usuarioId(), serializar(payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoDispararVerificacao(VerificacaoKycDisparadaEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("solicitacaoId", event.solicitacaoId().toString());
        payload.put("idVerificacaoExterna", event.idVerificacaoExterna());
        auditLogService.gravar(TipoEventoSeguranca.KYC_VERIFICACAO_DISPARADA, event.usuarioId(), serializar(payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoFinalizar(OnboardingFinalizadoEvent event) {
        TipoEventoSeguranca tipo = mapearTipoFinal(event.statusFinal());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("solicitacaoId", event.solicitacaoId().toString());
        payload.put("idVerificacaoExterna", event.idVerificacaoExterna());
        payload.put("statusFinal", event.statusFinal().name());
        auditLogService.gravar(tipo, event.usuarioId(), serializar(payload));
    }

    private String serializar(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            // Cai em fallback minimo so com solicitacaoId — nunca propagar como erro para o caller
            // do evento; auditoria nao pode quebrar o fluxo de negocio.
            log.warn("Falha ao serializar detalhes de audit KYC; usando fallback minimo");
            return "{\"solicitacaoId\":\"" + payload.getOrDefault("solicitacaoId", "") + "\"}";
        }
    }

    private static TipoEventoSeguranca mapearTipoFinal(StatusOnboarding statusFinal) {
        return switch (statusFinal) {
            case APROVADO -> TipoEventoSeguranca.KYC_FINALIZADO_APROVADO;
            case REPROVADO -> TipoEventoSeguranca.KYC_FINALIZADO_REPROVADO;
            case PENDENCIA -> TipoEventoSeguranca.KYC_FINALIZADO_PENDENCIA;
            default -> throw new IllegalArgumentException(
                    "OnboardingFinalizadoEvent com status nao-final: " + statusFinal);
        };
    }
}
