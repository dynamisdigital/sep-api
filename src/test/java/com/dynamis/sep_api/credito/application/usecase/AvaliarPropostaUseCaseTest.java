package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.service.PropostaAvaliacaoTransacional;
import com.dynamis.sep_api.credito.application.service.dto.ResultadoAvaliacaoCredito;
import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvaliarPropostaUseCaseTest {

    private PropostaAvaliacaoTransacional transacional;
    private AvaliarPropostaUseCase useCase;

    @BeforeEach
    void setup() {
        transacional = mock(PropostaAvaliacaoTransacional.class);
        useCase = new AvaliarPropostaUseCase(transacional);
    }

    @Test
    void happyPathRetornaResultadoDoTransacional() {
        UUID propostaId = UUID.randomUUID();
        ResultadoAvaliacaoCredito esperado =
                new ResultadoAvaliacaoCredito(900, StatusProposta.PRE_APROVADA, 0, 0, List.of());
        when(transacional.avaliar(propostaId)).thenReturn(esperado);

        ResultadoAvaliacaoCredito r = useCase.executar(propostaId);

        assertThat(r).isSameAs(esperado);
        verify(transacional).avaliar(propostaId);
    }

    @Test
    void propostaNaoEncontradaPropagaSemTentarPendencia() {
        UUID propostaId = UUID.randomUUID();
        when(transacional.avaliar(propostaId)).thenThrow(new PropostaNaoEncontradaException(propostaId));

        assertThatThrownBy(() -> useCase.executar(propostaId)).isInstanceOf(PropostaNaoEncontradaException.class);
        verify(transacional, org.mockito.Mockito.never()).moverParaPendencia(propostaId);
    }

    @Test
    void falhaNoMotorMovePropostaParaPendenciaEmTransacaoSeparada() {
        UUID propostaId = UUID.randomUUID();
        when(transacional.avaliar(propostaId)).thenThrow(new RuntimeException("motor falhou"));

        ResultadoAvaliacaoCredito r = useCase.executar(propostaId);

        assertThat(r.statusSugerido()).isEqualTo(StatusProposta.PENDENCIA);
        verify(transacional).moverParaPendencia(propostaId);
    }

    @Test
    void falhaNaPersistenciaTambemAcionaMoverParaPendencia() {
        UUID propostaId = UUID.randomUUID();
        when(transacional.avaliar(propostaId))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("db down"));

        ResultadoAvaliacaoCredito r = useCase.executar(propostaId);

        assertThat(r.statusSugerido()).isEqualTo(StatusProposta.PENDENCIA);
        verify(transacional).moverParaPendencia(propostaId);
    }

    @Test
    void falhaTambemNoMoverPendenciaNaoExplode() {
        UUID propostaId = UUID.randomUUID();
        when(transacional.avaliar(propostaId)).thenThrow(new RuntimeException("happy path falhou"));
        doThrow(new RuntimeException("pendencia tb falhou")).when(transacional).moverParaPendencia(propostaId);

        ResultadoAvaliacaoCredito r = useCase.executar(propostaId);

        assertThat(r.statusSugerido()).isEqualTo(StatusProposta.PENDENCIA);
    }
}
