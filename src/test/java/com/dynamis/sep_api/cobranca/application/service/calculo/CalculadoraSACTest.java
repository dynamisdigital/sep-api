package com.dynamis.sep_api.cobranca.application.service.calculo;

import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ParametrosCalculo;
import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ParcelaCalculada;
import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ResultadoCalculo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraSACTest {

    private final CalculadoraSAC sac = new CalculadoraSAC();

    @Test
    void sistema_retornaSac() {
        assertThat(sac.sistema()).isEqualTo(SistemaAmortizacao.SAC);
    }

    @Test
    void sac_12_parcelas_amortizacaoConstante_jurosDecrescentes() {
        ParametrosCalculo p = new ParametrosCalculo(
                new BigDecimal("12000"),
                new BigDecimal("0.02"),
                12,
                LocalDate.of(2026, 1, 1),
                SistemaAmortizacao.SAC,
                30,
                30);

        ResultadoCalculo r = sac.calcular(p);

        assertThat(r.parcelas()).hasSize(12);

        // Amortizacoes 1..n-1 sao iguais (1000.00 = 12000/12); ultima ajusta residual.
        for (int i = 0; i < 11; i++) {
            assertThat(r.parcelas().get(i).principal()).isEqualByComparingTo("1000.00");
        }

        BigDecimal somaPrincipal =
                r.parcelas().stream().map(ParcelaCalculada::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaPrincipal).isEqualByComparingTo("12000.00");

        // Juros decrescentes
        BigDecimal jurosP1 = r.parcelas().get(0).juros();
        BigDecimal jurosP12 = r.parcelas().get(11).juros();
        assertThat(jurosP1).isGreaterThan(jurosP12);

        // Parcela total decrescente
        BigDecimal totalP1 = r.parcelas().get(0).total();
        BigDecimal totalP12 = r.parcelas().get(11).total();
        assertThat(totalP1).isGreaterThan(totalP12);
    }

    @Test
    void sac_24_parcelas_somaPrincipalFecha() {
        ParametrosCalculo p = new ParametrosCalculo(
                new BigDecimal("50000"),
                new BigDecimal("0.015"),
                24,
                LocalDate.of(2026, 1, 1),
                SistemaAmortizacao.SAC,
                30,
                30);

        ResultadoCalculo r = sac.calcular(p);

        BigDecimal somaPrincipal =
                r.parcelas().stream().map(ParcelaCalculada::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaPrincipal).isEqualByComparingTo("50000.00");
    }

    @Test
    void sac_taxaZero_amortizacaoIgualSemJuros() {
        ParametrosCalculo p = new ParametrosCalculo(
                new BigDecimal("1200"), BigDecimal.ZERO, 12, LocalDate.of(2026, 1, 1), SistemaAmortizacao.SAC, 30, 30);

        ResultadoCalculo r = sac.calcular(p);

        assertThat(r.parcelas()).allSatisfy(parc -> assertThat(parc.juros()).isEqualByComparingTo("0.00"));
        BigDecimal somaPrincipal =
                r.parcelas().stream().map(ParcelaCalculada::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaPrincipal).isEqualByComparingTo("1200.00");
    }

    @Test
    void sac_n_1_parcela_unica_concentraTudo() {
        ParametrosCalculo p = new ParametrosCalculo(
                new BigDecimal("1000"),
                new BigDecimal("0.02"),
                1,
                LocalDate.of(2026, 1, 1),
                SistemaAmortizacao.SAC,
                30,
                30);

        ResultadoCalculo r = sac.calcular(p);

        assertThat(r.parcelas()).hasSize(1);
        assertThat(r.parcelas().get(0).principal()).isEqualByComparingTo("1000.00");
        assertThat(r.parcelas().get(0).juros()).isEqualByComparingTo("20.00");
    }

    @Test
    void sac_residual_consolidadoUltimaParcela() {
        // 100 / 3 = 33.333... — residual deve fechar com a soma.
        ParametrosCalculo p = new ParametrosCalculo(
                new BigDecimal("100"),
                new BigDecimal("0.01"),
                3,
                LocalDate.of(2026, 1, 1),
                SistemaAmortizacao.SAC,
                30,
                30);

        ResultadoCalculo r = sac.calcular(p);

        BigDecimal somaPrincipal =
                r.parcelas().stream().map(ParcelaCalculada::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaPrincipal).isEqualByComparingTo("100.00");
    }
}
