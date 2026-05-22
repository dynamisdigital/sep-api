package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.ConsultarParcelasQuery;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultarParcelasUseCaseTest {

    private ParcelaCobrancaRepository parcelaRepository;
    private ConsultarParcelasUseCase useCase;

    @BeforeEach
    void setup() {
        parcelaRepository = mock(ParcelaCobrancaRepository.class);
        useCase = new ConsultarParcelasUseCase(parcelaRepository);
    }

    @Test
    void filtroPorContratoEStatus_retornaSomenteCorrespondentes() {
        UUID contratoId = UUID.randomUUID();
        ParcelaCobranca p1 = novaParcela(contratoId, 1, LocalDate.of(2026, 1, 1));
        ParcelaCobranca p2 = novaParcela(contratoId, 2, LocalDate.of(2026, 2, 1));
        p2.marcarAtrasada();
        ParcelaCobranca p3 = novaParcela(contratoId, 3, LocalDate.of(2026, 3, 1));
        when(parcelaRepository.findByAgenda_ContratoIdOrderByNumeroAsc(contratoId))
                .thenReturn(List.of(p1, p2, p3));

        List<ParcelaCobranca> r =
                useCase.executar(new ConsultarParcelasQuery(contratoId, StatusParcela.PENDENTE, null, null));

        assertThat(r).extracting(ParcelaCobranca::getNumero).containsExactly(1, 3);
    }

    @Test
    void filtroPorIntervaloDataVencimento() {
        UUID contratoId = UUID.randomUUID();
        ParcelaCobranca p1 = novaParcela(contratoId, 1, LocalDate.of(2026, 1, 1));
        ParcelaCobranca p2 = novaParcela(contratoId, 2, LocalDate.of(2026, 2, 15));
        ParcelaCobranca p3 = novaParcela(contratoId, 3, LocalDate.of(2026, 3, 31));
        when(parcelaRepository.findByAgenda_ContratoIdOrderByNumeroAsc(contratoId))
                .thenReturn(List.of(p1, p2, p3));

        List<ParcelaCobranca> r = useCase.executar(
                new ConsultarParcelasQuery(contratoId, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)));

        assertThat(r).extracting(ParcelaCobranca::getNumero).containsExactly(2);
    }

    @Test
    void semContratoId_usaFindAll() {
        ParcelaCobranca p1 = novaParcela(UUID.randomUUID(), 1, LocalDate.of(2026, 1, 1));
        ParcelaCobranca p2 = novaParcela(UUID.randomUUID(), 1, LocalDate.of(2026, 2, 1));
        when(parcelaRepository.findAll()).thenReturn(List.of(p1, p2));

        List<ParcelaCobranca> r = useCase.executar(new ConsultarParcelasQuery(null, null, null, null));

        assertThat(r).hasSize(2);
    }

    @Test
    void ordenacaoDefaultPorDataVencimentoENumero() {
        UUID contratoId = UUID.randomUUID();
        ParcelaCobranca p3 = novaParcela(contratoId, 3, LocalDate.of(2026, 3, 1));
        ParcelaCobranca p1 = novaParcela(contratoId, 1, LocalDate.of(2026, 1, 1));
        ParcelaCobranca p2 = novaParcela(contratoId, 2, LocalDate.of(2026, 2, 1));
        when(parcelaRepository.findByAgenda_ContratoIdOrderByNumeroAsc(contratoId))
                .thenReturn(List.of(p3, p1, p2));

        List<ParcelaCobranca> r = useCase.executar(new ConsultarParcelasQuery(contratoId, null, null, null));

        assertThat(r).extracting(ParcelaCobranca::getNumero).containsExactly(1, 2, 3);
    }

    private static ParcelaCobranca novaParcela(UUID contratoId, int numero, LocalDate dataVencimento) {
        AgendaPagamento agenda = AgendaPagamento.criar(
                contratoId,
                List.of(new ParcelaPlanejada(
                        numero, ComposicaoValor.principalApenas(new BigDecimal("100.00")), dataVencimento)));
        return agenda.getParcelas().get(0);
    }
}
