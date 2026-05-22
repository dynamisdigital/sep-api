package com.dynamis.sep_api.cobranca.application.service.calculo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraJurosMoraTest {

    private final CalculadoraJurosMora calc = new CalculadoraJurosMora();

    private static final BigDecimal TAXA_1_PCT = new BigDecimal("0.01");

    @Test
    void semAtraso_retornaZero() {
        BigDecimal r = calc.calcular(
                new BigDecimal("100.00"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 15), TAXA_1_PCT);

        assertThat(r).isEqualByComparingTo("0.00");
    }

    @Test
    void dataReferenciaIgualVencimento_retornaZero() {
        BigDecimal r =
                calc.calcular(new BigDecimal("100.00"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1), TAXA_1_PCT);

        assertThat(r).isEqualByComparingTo("0.00");
    }

    @Test
    void atraso30Dias_taxaIntegralPorMes() {
        BigDecimal r = calc.calcular(
                new BigDecimal("1000.00"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1), TAXA_1_PCT);

        assertThat(r).isEqualByComparingTo("10.00");
    }

    @Test
    void atraso15Dias_proRataDie() {
        BigDecimal r = calc.calcular(
                new BigDecimal("1000.00"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 16), TAXA_1_PCT);

        // 1000 * 0.01 * 15 / 30 = 5.00
        assertThat(r).isEqualByComparingTo("5.00");
    }

    @Test
    void taxaZero_retornaZeroMesmoComAtraso() {
        BigDecimal r = calc.calcular(
                new BigDecimal("1000.00"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1), BigDecimal.ZERO);

        assertThat(r).isEqualByComparingTo("0.00");
    }

    @Test
    void valorBaseNegativo_rejeita() {
        assertThatThrownBy(() -> calc.calcular(
                        new BigDecimal("-1"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1), TAXA_1_PCT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
