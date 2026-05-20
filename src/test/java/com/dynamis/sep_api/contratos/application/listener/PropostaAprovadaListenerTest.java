package com.dynamis.sep_api.contratos.application.listener;

import com.dynamis.sep_api.contratos.application.usecase.GerarContratoUseCase;
import com.dynamis.sep_api.contratos.application.usecase.command.GerarContratoCommand;
import com.dynamis.sep_api.credito.domain.event.PropostaAprovadaEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PropostaAprovadaListenerTest {

    @Mock
    private GerarContratoUseCase gerarContratoUseCase;

    @InjectMocks
    private PropostaAprovadaListener listener;

    @Test
    void aoAprovar_disparaUseCaseComPropostaId() {
        UUID propostaId = UUID.randomUUID();
        PropostaAprovadaEvent event = new PropostaAprovadaEvent(propostaId, UUID.randomUUID(), UUID.randomUUID());

        listener.aoAprovar(event);

        verify(gerarContratoUseCase)
                .executar(argThat((GerarContratoCommand c) -> c.propostaId().equals(propostaId)));
    }

    @Test
    void aoAprovar_falhaDoUseCase_naoPropaga() {
        UUID propostaId = UUID.randomUUID();
        PropostaAprovadaEvent event = new PropostaAprovadaEvent(propostaId, UUID.randomUUID(), UUID.randomUUID());
        doThrow(new RuntimeException("template fail"))
                .when(gerarContratoUseCase)
                .executar(argThat((GerarContratoCommand c) -> c.propostaId().equals(propostaId)));

        // Nao deve lancar — listener engole a excecao e apenas loga
        listener.aoAprovar(event);
    }
}
