package com.dynamis.sep_api.cobranca.domain.model;

import com.dynamis.sep_api.cobranca.domain.exception.ParcelaEstadoInvalidoException;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParcelaCobrancaTest {

    @Test
    void registrarRecebimentoTotal_transicionaParaPaga() {
        ParcelaCobranca p = novaParcela("100.00");

        p.registrarRecebimento(
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                "comp-123",
                "key-1",
                null,
                UUID.randomUUID());

        assertThat(p.getStatus()).isEqualTo(StatusParcela.PAGA);
        assertThat(p.totalRecebido()).isEqualByComparingTo("100.00");
    }

    @Test
    void registrarRecebimentoParcial_transicionaParaParcialmentePaga() {
        ParcelaCobranca p = novaParcela("100.00");

        p.registrarRecebimento(
                new BigDecimal("40.00"),
                new BigDecimal("100.00"),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                "key-1",
                null,
                UUID.randomUUID());

        assertThat(p.getStatus()).isEqualTo(StatusParcela.PARCIALMENTE_PAGA);
        assertThat(p.totalRecebido()).isEqualByComparingTo("40.00");
    }

    @Test
    void overpayment_marcaPagaSemQuebrar() {
        ParcelaCobranca p = novaParcela("100.00");

        p.registrarRecebimento(
                new BigDecimal("150.00"),
                new BigDecimal("100.00"),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                "key-1",
                "excedente 50",
                UUID.randomUUID());

        assertThat(p.getStatus()).isEqualTo(StatusParcela.PAGA);
        assertThat(p.totalRecebido()).isEqualByComparingTo("150.00");
    }

    @Test
    void valorDevidoAtualizadoMaior_pagamentoDoOriginalNaoQuita() {
        // Parcela 100 atrasada com 5 de mora — pagamento de 100 vira PARCIALMENTE_PAGA, nao PAGA.
        ParcelaCobranca p = novaParcela("100.00");

        p.registrarRecebimento(
                new BigDecimal("100.00"),
                new BigDecimal("105.00"),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                "key-1",
                null,
                UUID.randomUUID());

        assertThat(p.getStatus()).isEqualTo(StatusParcela.PARCIALMENTE_PAGA);
        assertThat(p.totalRecebido()).isEqualByComparingTo("100.00");
    }

    @Test
    void recebimentoEmPaga_rejeita() {
        ParcelaCobranca p = novaParcela("100.00");
        p.registrarRecebimento(
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                "key-1",
                null,
                UUID.randomUUID());

        assertThatThrownBy(() -> p.registrarRecebimento(
                        new BigDecimal("10.00"),
                        new BigDecimal("100.00"),
                        OffsetDateTime.now(),
                        "TRANSFERENCIA",
                        null,
                        "key-2",
                        null,
                        UUID.randomUUID()))
                .isInstanceOf(ParcelaEstadoInvalidoException.class);
    }

    @Test
    void valorDevidoAtualizadoZero_rejeita() {
        ParcelaCobranca p = novaParcela("100.00");

        assertThatThrownBy(() -> p.registrarRecebimento(
                        new BigDecimal("10.00"),
                        BigDecimal.ZERO,
                        OffsetDateTime.now(),
                        "TRANSFERENCIA",
                        null,
                        "key-1",
                        null,
                        UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valorDevidoAtualizado");
    }

    @Test
    void marcarAtrasada_apenasPendente() {
        ParcelaCobranca p = novaParcela("100.00");
        p.marcarAtrasada();
        assertThat(p.getStatus()).isEqualTo(StatusParcela.ATRASADA);

        assertThatThrownBy(p::marcarAtrasada).isInstanceOf(ParcelaEstadoInvalidoException.class);
    }

    @Test
    void marcarInadimplente_apenasAtrasada() {
        ParcelaCobranca p = novaParcela("100.00");
        assertThatThrownBy(p::marcarInadimplente).isInstanceOf(ParcelaEstadoInvalidoException.class);

        p.marcarAtrasada();
        p.marcarInadimplente();

        assertThat(p.getStatus()).isEqualTo(StatusParcela.INADIMPLENTE);
        assertThatThrownBy(p::marcarInadimplente).isInstanceOf(ParcelaEstadoInvalidoException.class);
    }

    @Test
    void iniciarNegociacao_porAtrasada_retornaAnterior() {
        ParcelaCobranca p = novaParcela("100.00");
        p.marcarAtrasada();

        StatusParcela anterior = p.iniciarNegociacao();

        assertThat(anterior).isEqualTo(StatusParcela.ATRASADA);
        assertThat(p.getStatus()).isEqualTo(StatusParcela.EM_NEGOCIACAO);
        assertThat(p.getStatus().permiteRecebimento()).isFalse();
    }

    @Test
    void iniciarNegociacao_porInadimplente_retornaAnterior() {
        ParcelaCobranca p = novaParcela("100.00");
        p.marcarAtrasada();
        p.marcarInadimplente();

        StatusParcela anterior = p.iniciarNegociacao();

        assertThat(anterior).isEqualTo(StatusParcela.INADIMPLENTE);
        assertThat(p.getStatus()).isEqualTo(StatusParcela.EM_NEGOCIACAO);
    }

    @Test
    void iniciarNegociacao_porPendente_rejeita() {
        ParcelaCobranca p = novaParcela("100.00");

        assertThatThrownBy(p::iniciarNegociacao).isInstanceOf(ParcelaEstadoInvalidoException.class);
    }

    @Test
    void marcarRenegociada_apenasEmNegociacao() {
        ParcelaCobranca p = novaParcela("100.00");
        p.marcarAtrasada();
        p.iniciarNegociacao();

        p.marcarRenegociada();

        assertThat(p.getStatus()).isEqualTo(StatusParcela.RENEGOCIADA);
        assertThat(p.getStatus().isFinal()).isTrue();
    }

    @Test
    void reverterDeNegociacao_voltaParaAnterior() {
        ParcelaCobranca p = novaParcela("100.00");
        p.marcarAtrasada();
        p.marcarInadimplente();
        p.iniciarNegociacao();

        p.reverterDeNegociacao(StatusParcela.INADIMPLENTE);

        assertThat(p.getStatus()).isEqualTo(StatusParcela.INADIMPLENTE);
    }

    @Test
    void reverterDeNegociacao_paraStatusInvalido_rejeita() {
        ParcelaCobranca p = novaParcela("100.00");
        p.marcarAtrasada();
        p.iniciarNegociacao();

        assertThatThrownBy(() -> p.reverterDeNegociacao(StatusParcela.PAGA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statusAnterior");
    }

    @Test
    void recebimentoEmInadimplente_rejeita() {
        ParcelaCobranca p = novaParcela("100.00");
        p.marcarAtrasada();
        p.marcarInadimplente();

        assertThatThrownBy(() -> p.registrarRecebimento(
                        new BigDecimal("10.00"),
                        new BigDecimal("100.00"),
                        OffsetDateTime.now(),
                        "TRANSFERENCIA",
                        null,
                        "key-1",
                        null,
                        UUID.randomUUID()))
                .isInstanceOf(ParcelaEstadoInvalidoException.class);
    }

    @Test
    void recebimentoEmEmNegociacao_rejeita() {
        ParcelaCobranca p = novaParcela("100.00");
        p.marcarAtrasada();
        p.iniciarNegociacao();

        assertThatThrownBy(() -> p.registrarRecebimento(
                        new BigDecimal("10.00"),
                        new BigDecimal("100.00"),
                        OffsetDateTime.now(),
                        "TRANSFERENCIA",
                        null,
                        "key-1",
                        null,
                        UUID.randomUUID()))
                .isInstanceOf(ParcelaEstadoInvalidoException.class);
    }

    @Test
    void recebimentoPorIdempotencyKey_retornaExistente() {
        ParcelaCobranca p = novaParcela("100.00");
        Recebimento r = p.registrarRecebimento(
                new BigDecimal("50.00"),
                new BigDecimal("100.00"),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                "key-X",
                null,
                UUID.randomUUID());

        assertThat(p.recebimentoPorIdempotencyKey("key-X")).contains(r);
        assertThat(p.recebimentoPorIdempotencyKey("outra")).isEmpty();
    }

    private static ParcelaCobranca novaParcela(String valor) {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(new ParcelaPlanejada(
                        1,
                        ComposicaoValor.principalApenas(new BigDecimal(valor)),
                        LocalDate.now().plusDays(30))));
        return agenda.getParcelas().get(0);
    }
}
