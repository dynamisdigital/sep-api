package com.dynamis.sep_api.onboarding.application.listener;

import com.dynamis.sep_api.onboarding.application.usecase.IniciarPldEmpresaUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.IniciarPldPessoaUseCase;
import com.dynamis.sep_api.onboarding.domain.event.KybFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PldOrchestrationListenerTest {

    private IniciarPldPessoaUseCase pldPessoa;
    private IniciarPldEmpresaUseCase pldEmpresa;
    private PldOrchestrationListener listener;

    @BeforeEach
    void setup() {
        pldPessoa = mock(IniciarPldPessoaUseCase.class);
        pldEmpresa = mock(IniciarPldEmpresaUseCase.class);
        listener = new PldOrchestrationListener(pldPessoa, pldEmpresa);
    }

    @Test
    void kycAprovadoDisparaPldPessoa() {
        UUID solicitacaoId = UUID.randomUUID();
        listener.onKycFinalizado(
                new OnboardingFinalizadoEvent(solicitacaoId, UUID.randomUUID(), StatusOnboarding.APROVADO, "ext"));

        verify(pldPessoa).executar(any(UUID.class), anyString());
    }

    @Test
    void kycReprovadoNaoDisparaPld() {
        listener.onKycFinalizado(
                new OnboardingFinalizadoEvent(UUID.randomUUID(), UUID.randomUUID(), StatusOnboarding.REPROVADO, "ext"));

        verify(pldPessoa, never()).executar(any(), anyString());
    }

    @Test
    void kybAprovadoDisparaPldEmpresa() {
        UUID solicitacaoId = UUID.randomUUID();
        listener.onKybFinalizado(
                new KybFinalizadoEvent(solicitacaoId, UUID.randomUUID(), StatusOnboarding.APROVADO, UUID.randomUUID()));

        verify(pldEmpresa).executar(any(UUID.class), anyString());
    }

    @Test
    void kybReprovadoNaoDisparaPld() {
        listener.onKybFinalizado(new KybFinalizadoEvent(
                UUID.randomUUID(), UUID.randomUUID(), StatusOnboarding.REPROVADO, UUID.randomUUID()));

        verify(pldEmpresa, never()).executar(any(), anyString());
    }

    @Test
    void falhaNoPldNaoPropaga() {
        doThrow(new RuntimeException("provider down")).when(pldPessoa).executar(any(UUID.class), anyString());

        listener.onKycFinalizado(
                new OnboardingFinalizadoEvent(UUID.randomUUID(), UUID.randomUUID(), StatusOnboarding.APROVADO, "ext"));
        // sem exception escapando: listener log + segue
    }
}
