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
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                "comp-123",
                "key-1",
                null,
                UUID.randomUUID());

        assertThat(p.getStatus()).isEqualTo(StatusParcela.PAGA);
        assertThat(p.valorEmAberto()).isEqualByComparingTo("0.00");
    }

    @Test
    void registrarRecebimentoParcial_transicionaParaParcialmentePaga() {
        ParcelaCobranca p = novaParcela("100.00");

        p.registrarRecebimento(
                new BigDecimal("40.00"), OffsetDateTime.now(), "TRANSFERENCIA", null, "key-1", null, UUID.randomUUID());

        assertThat(p.getStatus()).isEqualTo(StatusParcela.PARCIALMENTE_PAGA);
        assertThat(p.valorEmAberto()).isEqualByComparingTo("60.00");
    }

    @Test
    void overpayment_marcaPagaESemSaldoNegativo() {
        ParcelaCobranca p = novaParcela("100.00");

        p.registrarRecebimento(
                new BigDecimal("150.00"),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                "key-1",
                "excedente 50",
                UUID.randomUUID());

        assertThat(p.getStatus()).isEqualTo(StatusParcela.PAGA);
        assertThat(p.valorEmAberto()).isEqualByComparingTo("0.00");
        assertThat(p.totalRecebido()).isEqualByComparingTo("150.00");
    }

    @Test
    void recebimentoEmPaga_rejeita() {
        ParcelaCobranca p = novaParcela("100.00");
        p.registrarRecebimento(
                new BigDecimal("100.00"),
                OffsetDateTime.now(),
                "TRANSFERENCIA",
                null,
                "key-1",
                null,
                UUID.randomUUID());

        assertThatThrownBy(() -> p.registrarRecebimento(
                        new BigDecimal("10.00"),
                        OffsetDateTime.now(),
                        "TRANSFERENCIA",
                        null,
                        "key-2",
                        null,
                        UUID.randomUUID()))
                .isInstanceOf(ParcelaEstadoInvalidoException.class);
    }

    @Test
    void marcarAtrasada_apenasPendente() {
        ParcelaCobranca p = novaParcela("100.00");
        p.marcarAtrasada();
        assertThat(p.getStatus()).isEqualTo(StatusParcela.ATRASADA);

        assertThatThrownBy(p::marcarAtrasada).isInstanceOf(ParcelaEstadoInvalidoException.class);
    }

    @Test
    void recebimentoPorIdempotencyKey_retornaExistente() {
        ParcelaCobranca p = novaParcela("100.00");
        Recebimento r = p.registrarRecebimento(
                new BigDecimal("50.00"), OffsetDateTime.now(), "TRANSFERENCIA", null, "key-X", null, UUID.randomUUID());

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
