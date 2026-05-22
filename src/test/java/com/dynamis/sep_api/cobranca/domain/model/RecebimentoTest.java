package com.dynamis.sep_api.cobranca.domain.model;

import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecebimentoTest {

    @Test
    void valorRecebidoZero_rejeita() {
        ParcelaCobranca p = novaParcela();

        assertThatThrownBy(() -> p.registrarRecebimento(
                        BigDecimal.ZERO, OffsetDateTime.now(), "TRANSFERENCIA", null, "key-1", null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idempotencyKeyVazia_rejeita() {
        ParcelaCobranca p = novaParcela();

        assertThatThrownBy(() -> p.registrarRecebimento(
                        new BigDecimal("10.00"),
                        OffsetDateTime.now(),
                        "TRANSFERENCIA",
                        null,
                        " ",
                        null,
                        UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyKey");
    }

    @Test
    void meioPagamentoVazio_rejeita() {
        ParcelaCobranca p = novaParcela();

        assertThatThrownBy(() -> p.registrarRecebimento(
                        new BigDecimal("10.00"), OffsetDateTime.now(), " ", null, "key-1", null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("meioPagamento");
    }

    @Test
    void registradoPorObrigatorio() {
        ParcelaCobranca p = novaParcela();

        assertThatThrownBy(() -> p.registrarRecebimento(
                        new BigDecimal("10.00"), OffsetDateTime.now(), "TRANSFERENCIA", null, "key-1", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static ParcelaCobranca novaParcela() {
        return AgendaPagamento.criar(
                        UUID.randomUUID(),
                        List.of(new ParcelaPlanejada(
                                1,
                                ComposicaoValor.principalApenas(new BigDecimal("100.00")),
                                LocalDate.now().plusDays(30))))
                .getParcelas()
                .get(0);
    }
}
