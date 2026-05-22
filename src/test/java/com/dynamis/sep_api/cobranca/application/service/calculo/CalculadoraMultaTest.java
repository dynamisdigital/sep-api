package com.dynamis.sep_api.cobranca.application.service.calculo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraMultaTest {

    private final CalculadoraMulta calc = new CalculadoraMulta();
    private static final BigDecimal MULTA_2_PCT = new BigDecimal("0.02");

    @Test
    void semAtraso_retornaZero() {
        BigDecimal r = calc.calcular(
                new BigDecimal("100.00"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 15), MULTA_2_PCT);

        assertThat(r).isEqualByComparingTo("0.00");
    }

    @Test
    void atraso_aplicaPercentualOneShot() {
        BigDecimal r = calc.calcular(
                new BigDecimal("1000.00"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), MULTA_2_PCT);

        assertThat(r).isEqualByComparingTo("20.00");
    }

    @Test
    void multaZero_retornaZero() {
        BigDecimal r = calc.calcular(
                new BigDecimal("1000.00"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1), BigDecimal.ZERO);

        assertThat(r).isEqualByComparingTo("0.00");
    }

    @Test
    void percentualNegativo_rejeita() {
        assertThatThrownBy(() -> calc.calcular(
                        new BigDecimal("1000"),
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 7, 1),
                        new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
