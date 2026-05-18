package com.dynamis.sep_api.credito.domain.vo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void brlFactoryNormalizaEscala2() {
        Money m = Money.brl("100");
        assertThat(m.valor()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(m.valor().scale()).isEqualTo(2);
        assertThat(m.moeda()).isEqualTo("BRL");
    }

    @Test
    void halfUpEmTerceiraCasa() {
        Money m = Money.brl(new BigDecimal("10.005"));
        assertThat(m.valor()).isEqualByComparingTo(new BigDecimal("10.01"));
    }

    @Test
    void valorZeroRejeitado() {
        assertThatThrownBy(() -> Money.brl("0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    void valorNegativoRejeitado() {
        assertThatThrownBy(() -> Money.brl(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    void moedaVaziaRejeitada() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10"), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vazia");
    }

    @Test
    void moedaNulaRejeitada() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10"), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void valorNuloRejeitado() {
        assertThatThrownBy(() -> new Money(null, "BRL")).isInstanceOf(NullPointerException.class);
    }
}
