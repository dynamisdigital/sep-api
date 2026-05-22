package com.dynamis.sep_api.cobranca.domain.model;

import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgendaPagamentoTest {

    @Test
    void criar_montaParcelasEValorTotal() {
        UUID contratoId = UUID.randomUUID();
        List<ParcelaPlanejada> planejadas = List.of(
                new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 6, 1)),
                new ParcelaPlanejada(
                        2, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 7, 1)));

        AgendaPagamento agenda = AgendaPagamento.criar(contratoId, planejadas);

        assertThat(agenda.getContratoId()).isEqualTo(contratoId);
        assertThat(agenda.getNumeroParcelas()).isEqualTo(2);
        assertThat(agenda.getValorTotal()).isEqualByComparingTo("200.00");
        assertThat(agenda.getParcelas()).hasSize(2);
        assertThat(agenda.getParcelas()).extracting(ParcelaCobranca::getStatus).containsOnly(StatusParcela.PENDENTE);
        assertThat(agenda.getParcelas()).extracting(ParcelaCobranca::getNumero).containsExactly(1, 2);
    }

    @Test
    void agendaSemParcelas_rejeita() {
        assertThatThrownBy(() -> AgendaPagamento.criar(UUID.randomUUID(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numerosParcelasDuplicados_rejeita() {
        List<ParcelaPlanejada> dup = List.of(
                new ParcelaPlanejada(1, ComposicaoValor.principalApenas(new BigDecimal("100")), LocalDate.now()),
                new ParcelaPlanejada(
                        1,
                        ComposicaoValor.principalApenas(new BigDecimal("100")),
                        LocalDate.now().plusMonths(1)));

        assertThatThrownBy(() -> AgendaPagamento.criar(UUID.randomUUID(), dup))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicado");
    }

    @Test
    void parcelasExpostasImutaveis() {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.now())));

        assertThatThrownBy(() -> agenda.getParcelas().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
