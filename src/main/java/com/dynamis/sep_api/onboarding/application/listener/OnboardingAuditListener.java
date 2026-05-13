package com.dynamis.sep_api.onboarding.application.listener;

import com.dynamis.sep_api.onboarding.domain.event.DocumentoCadastralEnviadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingIniciadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.VerificacaoKycDisparadaEvent;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Liga eventos de dominio do modulo {@code onboarding} ao {@code audit_log_seguranca} (Sprint 5).
 *
 * <p>Usa {@link TransactionalEventListener} com {@link TransactionPhase#AFTER_COMMIT} para evitar
 * gravar trilha de auditoria de uma transacao que foi revertida. Em caso de rollback do use case
 * que publicou o evento, nenhum audit log e gerado.
 *
 * <p>LGPD/CMN 4.656/2018: detalhes contem APENAS identificadores tecnicos e metadados
 * sanitizados (solicitacaoId, idVerificacaoExterna, tipoDocumento, sha256, statusFinal). CPF
 * completo, nome e payload bruto do provider NUNCA entram aqui — payload bruto vive em
 * {@code resultado_verificacao.payload_provider}.
 */
@Component
public class OnboardingAuditListener {

    private final AuditLogSegurancaService auditLogService;

    public OnboardingAuditListener(AuditLogSegurancaService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoIniciar(OnboardingIniciadoEvent event) {
        String detalhes = "{\"solicitacaoId\":\"" + event.solicitacaoId() + "\"}";
        auditLogService.gravar(TipoEventoSeguranca.KYC_INICIADO, event.usuarioId(), detalhes);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoEnviarDocumento(DocumentoCadastralEnviadoEvent event) {
        String detalhes = "{\"solicitacaoId\":\"" + event.solicitacaoId() + "\","
                + "\"documentoId\":\"" + event.documentoId() + "\","
                + "\"tipoDocumento\":\"" + event.tipo() + "\","
                + "\"sha256\":\"" + event.sha256() + "\"}";
        auditLogService.gravar(TipoEventoSeguranca.KYC_DOCUMENTO_ENVIADO, event.usuarioId(), detalhes);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoDispararVerificacao(VerificacaoKycDisparadaEvent event) {
        String detalhes = "{\"solicitacaoId\":\"" + event.solicitacaoId() + "\"," + "\"idVerificacaoExterna\":\""
                + event.idVerificacaoExterna() + "\"}";
        auditLogService.gravar(TipoEventoSeguranca.KYC_VERIFICACAO_DISPARADA, event.usuarioId(), detalhes);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoFinalizar(OnboardingFinalizadoEvent event) {
        TipoEventoSeguranca tipo = mapearTipoFinal(event.statusFinal());
        String detalhes = "{\"solicitacaoId\":\"" + event.solicitacaoId() + "\","
                + "\"idVerificacaoExterna\":\"" + event.idVerificacaoExterna() + "\","
                + "\"statusFinal\":\"" + event.statusFinal() + "\"}";
        auditLogService.gravar(tipo, event.usuarioId(), detalhes);
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
