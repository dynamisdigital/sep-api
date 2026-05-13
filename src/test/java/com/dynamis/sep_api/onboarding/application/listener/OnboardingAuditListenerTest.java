package com.dynamis.sep_api.onboarding.application.listener;

import com.dynamis.sep_api.onboarding.domain.event.DocumentoCadastralEnviadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingIniciadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.VerificacaoKycDisparadaEvent;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OnboardingAuditListenerTest {

    private AuditLogSegurancaService auditService;
    private OnboardingAuditListener listener;

    private UUID solicitacaoId;
    private UUID usuarioId;

    @BeforeEach
    void setup() {
        auditService = mock(AuditLogSegurancaService.class);
        listener = new OnboardingAuditListener(auditService);
        solicitacaoId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
    }

    @Test
    void onboardingIniciadoGravaKycIniciadoComSolicitacaoId() {
        listener.aoIniciar(new OnboardingIniciadoEvent(solicitacaoId, usuarioId));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService).gravar(eq(TipoEventoSeguranca.KYC_INICIADO), eq(usuarioId), detalhes.capture());
        assertThat(detalhes.getValue()).contains(solicitacaoId.toString());
    }

    @Test
    void documentoEnviadoGravaKycDocumentoEnviadoComMetadados() {
        UUID documentoId = UUID.randomUUID();
        listener.aoEnviarDocumento(
                new DocumentoCadastralEnviadoEvent(solicitacaoId, usuarioId, documentoId, TipoDocumento.RG, "sha-abc"));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService).gravar(eq(TipoEventoSeguranca.KYC_DOCUMENTO_ENVIADO), eq(usuarioId), detalhes.capture());
        assertThat(detalhes.getValue())
                .contains(solicitacaoId.toString())
                .contains(documentoId.toString())
                .contains("RG")
                .contains("sha-abc");
    }

    @Test
    void verificacaoDisparadaGravaKycVerificacaoDisparadaComIdExterno() {
        listener.aoDispararVerificacao(new VerificacaoKycDisparadaEvent(solicitacaoId, usuarioId, "ext-1"));

        ArgumentCaptor<String> detalhes = ArgumentCaptor.forClass(String.class);
        verify(auditService)
                .gravar(eq(TipoEventoSeguranca.KYC_VERIFICACAO_DISPARADA), eq(usuarioId), detalhes.capture());
        assertThat(detalhes.getValue()).contains(solicitacaoId.toString()).contains("ext-1");
    }

    @Test
    void finalizadoAprovadoMapeiaParaKycFinalizadoAprovado() {
        listener.aoFinalizar(
                new OnboardingFinalizadoEvent(solicitacaoId, usuarioId, StatusOnboarding.APROVADO, "ext-1"));

        verify(auditService)
                .gravar(
                        eq(TipoEventoSeguranca.KYC_FINALIZADO_APROVADO),
                        eq(usuarioId),
                        org.mockito.ArgumentMatchers.contains("APROVADO"));
    }

    @Test
    void finalizadoReprovadoMapeiaParaKycFinalizadoReprovado() {
        listener.aoFinalizar(
                new OnboardingFinalizadoEvent(solicitacaoId, usuarioId, StatusOnboarding.REPROVADO, "ext-2"));

        verify(auditService)
                .gravar(
                        eq(TipoEventoSeguranca.KYC_FINALIZADO_REPROVADO),
                        eq(usuarioId),
                        org.mockito.ArgumentMatchers.contains("REPROVADO"));
    }

    @Test
    void finalizadoPendenciaMapeiaParaKycFinalizadoPendencia() {
        listener.aoFinalizar(
                new OnboardingFinalizadoEvent(solicitacaoId, usuarioId, StatusOnboarding.PENDENCIA, "ext-3"));

        verify(auditService)
                .gravar(
                        eq(TipoEventoSeguranca.KYC_FINALIZADO_PENDENCIA),
                        eq(usuarioId),
                        org.mockito.ArgumentMatchers.contains("PENDENCIA"));
    }

    @Test
    void finalizadoComStatusNaoFinalRejeitadoSemGravarAuditLog() {
        // OnboardingFinalizadoEvent NUNCA deve carregar status nao-final (Task 6.4
        // garante via ResultadoVerificacao.registrar guard); este teste protege contra regressao.
        OnboardingFinalizadoEvent invalido =
                new OnboardingFinalizadoEvent(solicitacaoId, usuarioId, StatusOnboarding.INICIADO, "ext-bad");

        assertThatThrownBy(() -> listener.aoFinalizar(invalido)).isInstanceOf(IllegalArgumentException.class);
    }
}
