package com.dynamis.sep_api.cobranca.application.service.calculo;

import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ParametrosCalculo;
import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ParcelaCalculada;
import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ResultadoCalculo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraPriceTest {

    private final CalculadoraPrice price = new CalculadoraPrice();

    @Test
    void sistema_retornaPrice() {
        assertThat(price.sistema()).isEqualTo(SistemaAmortizacao.PRICE);
    }

    @Test
    void price_12_parcelas_2_por_cento_somaPrincipalFechaComValorFinanciado() {
        ParametrosCalculo p = new ParametrosCalculo(
                new BigDecimal("10000"),
                new BigDecimal("0.02"),
                12,
                LocalDate.of(2026, 1, 1),
                SistemaAmortizacao.PRICE,
                30,
                30);

        ResultadoCalculo r = price.calcular(p);

        assertThat(r.parcelas()).hasSize(12);
        assertThat(r.parcelas())
                .extracting(ParcelaCalculada::numero)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);

        BigDecimal somaPrincipal =
                r.parcelas().stream().map(ParcelaCalculada::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaPrincipal).isEqualByComparingTo("10000.00");

        // Juros decrescentes
        BigDecimal jurosP1 = r.parcelas().get(0).juros();
        BigDecimal jurosP12 = r.parcelas().get(11).juros();
        assertThat(jurosP1).isGreaterThan(jurosP12);

        // Amortizacao crescente (exceto possivel ajuste residual na ultima)
        BigDecimal amortP1 = r.parcelas().get(0).principal();
        BigDecimal amortP11 = r.parcelas().get(10).principal();
        assertThat(amortP11).isGreaterThan(amortP1);
    }

    @Test
    void price_24_parcelas_1_5_por_cento_somaPrincipalFecha() {
        ParametrosCalculo p = new ParametrosCalculo(
                new BigDecimal("50000"),
                new BigDecimal("0.015"),
                24,
                LocalDate.of(2026, 1, 1),
                SistemaAmortizacao.PRICE,
                30,
                30);

        ResultadoCalculo r = price.calcular(p);

        assertThat(r.parcelas()).hasSize(24);
        BigDecimal somaPrincipal =
                r.parcelas().stream().map(ParcelaCalculada::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaPrincipal).isEqualByComparingTo("50000.00");
    }

    @Test
    void price_taxaZero_distribuiPrincipalIgualSemJuros() {
        ParametrosCalculo p = new ParametrosCalculo(
                new BigDecimal("1200"),
                BigDecimal.ZERO,
                12,
                LocalDate.of(2026, 1, 1),
                SistemaAmortizacao.PRICE,
                30,
                30);

        ResultadoCalculo r = price.calcular(p);

        assertThat(r.parcelas()).hasSize(12);
        BigDecimal somaPrincipal =
                r.parcelas().stream().map(ParcelaCalculada::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaPrincipal).isEqualByComparingTo("1200.00");
        assertThat(r.parcelas()).allSatisfy(parc -> assertThat(parc.juros()).isEqualByComparingTo("0.00"));
        assertThat(r.parcelas().get(0).principal()).isEqualByComparingTo("100.00");
    }

    @Test
    void price_residual_consolidadoUltimaParcela() {
        // Valor com decimais que geram residual de arredondamento
        ParametrosCalculo p = new ParametrosCalculo(
                new BigDecimal("1000"),
                new BigDecimal("0.03"),
                3,
                LocalDate.of(2026, 1, 1),
                SistemaAmortizacao.PRICE,
                30,
                30);

        ResultadoCalculo r = price.calcular(p);

        BigDecimal somaPrincipal =
                r.parcelas().stream().map(ParcelaCalculada::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Soma deve fechar exatamente com valorFinanciado, mesmo com arredondamento.
        assertThat(somaPrincipal).isEqualByComparingTo("1000.00");
    }

    @Test
    void price_vencimentos_30_em_30_dias_a_partir_da_data_base() {
        LocalDate dataBase = LocalDate.of(2026, 1, 15);
        ParametrosCalculo p = new ParametrosCalculo(
                new BigDecimal("1000"), new BigDecimal("0.02"), 3, dataBase, SistemaAmortizacao.PRICE, 30, 30);

        ResultadoCalculo r = price.calcular(p);

        assertThat(r.parcelas().get(0).dataVencimento()).isEqualTo(LocalDate.of(2026, 2, 14));
        assertThat(r.parcelas().get(1).dataVencimento()).isEqualTo(LocalDate.of(2026, 3, 16));
        assertThat(r.parcelas().get(2).dataVencimento()).isEqualTo(LocalDate.of(2026, 4, 15));
    }
}
