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
    void aoAprovar_disparaUseCaseComPropostaIdEParecerOrigem() {
        UUID propostaId = UUID.randomUUID();
        UUID parecerId = UUID.randomUUID();
        PropostaAprovadaEvent event = new PropostaAprovadaEvent(propostaId, UUID.randomUUID(), parecerId);

        listener.aoAprovar(event);

        verify(gerarContratoUseCase)
                .executar(argThat((GerarContratoCommand c) ->
                        c.propostaId().equals(propostaId) && parecerId.equals(c.parecerOrigemId())));
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

    @Test
    void aoAprovar_eventoNull_naoLancaENaoChamaUseCase() {
        // Defensivo: try cobre tudo (Sprint 10 fix code review)
        listener.aoAprovar(null);
        org.mockito.Mockito.verifyNoInteractions(gerarContratoUseCase);
    }

    @Test
    void aoAprovar_replayDoMesmoEvento_chamaUseCaseDuasVezesComMesmoParecerOrigem() {
        // Listener nao mantem estado proprio — replay simplesmente repassa pra use case com o
        // mesmo parecerOrigemId. Idempotencia real esta no GerarContratoUseCase, que detecta
        // versao vigente com mesmo parecer e faz short-circuit.
        UUID propostaId = UUID.randomUUID();
        UUID parecerId = UUID.randomUUID();
        PropostaAprovadaEvent event = new PropostaAprovadaEvent(propostaId, UUID.randomUUID(), parecerId);

        listener.aoAprovar(event);
        listener.aoAprovar(event);

        org.mockito.Mockito.verify(gerarContratoUseCase, org.mockito.Mockito.times(2))
                .executar(argThat((GerarContratoCommand c) ->
                        c.propostaId().equals(propostaId) && parecerId.equals(c.parecerOrigemId())));
    }
}
