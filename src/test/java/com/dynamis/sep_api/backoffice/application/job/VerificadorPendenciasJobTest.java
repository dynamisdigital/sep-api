package com.dynamis.sep_api.backoffice.application.job;

import com.dynamis.sep_api.backoffice.application.listener.ContratoSemAssinaturaListener;
import com.dynamis.sep_api.backoffice.application.listener.PropostaPendenciaListener;
import com.dynamis.sep_api.backoffice.application.listener.WebhookFalhouListener;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VerificadorPendenciasJobTest {

    @Test
    void executar_invocaTresListenersNaOrdem() {
        PropostaPendenciaListener proposta = mock(PropostaPendenciaListener.class);
        ContratoSemAssinaturaListener contrato = mock(ContratoSemAssinaturaListener.class);
        WebhookFalhouListener webhook = mock(WebhookFalhouListener.class);

        new VerificadorPendenciasJob(proposta, contrato, webhook).executar();

        verify(proposta).verificar();
        verify(contrato).verificar();
        verify(webhook).verificar();
    }

    @Test
    void executar_falhaIsoladaNaoQuebraOsDemais() {
        PropostaPendenciaListener proposta = mock(PropostaPendenciaListener.class);
        ContratoSemAssinaturaListener contrato = mock(ContratoSemAssinaturaListener.class);
        WebhookFalhouListener webhook = mock(WebhookFalhouListener.class);
        doThrow(new RuntimeException("boom")).when(proposta).verificar();

        new VerificadorPendenciasJob(proposta, contrato, webhook).executar();

        verify(contrato).verificar();
        verify(webhook).verificar();
    }
}
