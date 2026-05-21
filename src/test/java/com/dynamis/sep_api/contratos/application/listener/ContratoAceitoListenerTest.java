package com.dynamis.sep_api.contratos.application.listener;

import com.dynamis.sep_api.contratos.application.usecase.EnviarParaAssinaturaUseCase;
import com.dynamis.sep_api.contratos.domain.event.ContratoAceitoEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ContratoAceitoListenerTest {

    @Test
    void aoAceitar_disparaEnvioComContratoId() {
        EnviarParaAssinaturaUseCase useCase = mock(EnviarParaAssinaturaUseCase.class);
        ContratoAceitoListener listener = new ContratoAceitoListener(useCase);
        UUID contratoId = UUID.randomUUID();
        ContratoAceitoEvent event = new ContratoAceitoEvent(
                contratoId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "h", "127.0.0.1", "agent");

        listener.aoAceitar(event);

        verify(useCase).executar(eq(contratoId), eq("aceite:" + contratoId));
    }

    @Test
    void aoAceitar_falhaDoEnvio_naoPropaga() {
        EnviarParaAssinaturaUseCase useCase = mock(EnviarParaAssinaturaUseCase.class);
        doThrow(new RuntimeException("provider fora")).when(useCase).executar(any(UUID.class), anyString());
        ContratoAceitoListener listener = new ContratoAceitoListener(useCase);
        ContratoAceitoEvent event = new ContratoAceitoEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "h",
                "127.0.0.1",
                "agent");

        // nao lanca
        listener.aoAceitar(event);
    }
}
