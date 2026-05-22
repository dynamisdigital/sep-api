package com.dynamis.sep_api.cobranca.domain.vo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComposicaoValorTest {

    @Test
    void totalEhSomaDosComponentes() {
        ComposicaoValor c = ComposicaoValor.de(
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("5.00"), new BigDecimal("2.00"));

        assertThat(c.total()).isEqualByComparingTo("117.00");
    }

    @Test
    void normalizaParaEscala2() {
        ComposicaoValor c = ComposicaoValor.principalApenas(new BigDecimal("100.123"));

        assertThat(c.principal()).isEqualByComparingTo("100.12");
        assertThat(c.total()).isEqualByComparingTo("100.12");
    }

    @Test
    void principalDeveSerPositivo() {
        assertThatThrownBy(() -> ComposicaoValor.principalApenas(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("principal");
    }

    @Test
    void componentesNaoPodemSerNegativos() {
        assertThatThrownBy(() -> ComposicaoValor.de(
                        new BigDecimal("100"), new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("juros");
    }

    @Test
    void jurosMultaEncargosPodemSerZero() {
        ComposicaoValor c = ComposicaoValor.principalApenas(new BigDecimal("500.00"));

        assertThat(c.juros()).isEqualByComparingTo("0.00");
        assertThat(c.multa()).isEqualByComparingTo("0.00");
        assertThat(c.encargos()).isEqualByComparingTo("0.00");
    }
}
