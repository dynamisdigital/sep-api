package com.dynamis.sep_api.cobranca.application.service.calculo;

import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ParametrosCalculo;
import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ParcelaCalculada;
import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ResultadoCalculo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Amortizacao Price: parcela total constante, juros decrescentes, amortizacao crescente. Quando a
 * taxa for zero, distribui {@code valorFinanciado / n} igual em todas as parcelas. Diferenca
 * residual de arredondamento eh consolidada no principal da ultima parcela.
 */
@Component
public class CalculadoraPrice implements CalculadoraAmortizacao {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int SCALE = 2;

    @Override
    public SistemaAmortizacao sistema() {
        return SistemaAmortizacao.PRICE;
    }

    @Override
    public ResultadoCalculo calcular(ParametrosCalculo p) {
        BigDecimal valorFinanciado = p.valorFinanciado();
        BigDecimal taxa = p.taxaMensal();
        int n = p.numeroParcelas();

        BigDecimal parcelaFixa = calcularParcelaFixa(valorFinanciado, taxa, n);
        List<ParcelaCalculada> parcelas = new ArrayList<>(n);
        BigDecimal saldo = valorFinanciado;
        BigDecimal somaPrincipal = BigDecimal.ZERO;
        BigDecimal somaTotal = BigDecimal.ZERO;

        for (int i = 1; i <= n; i++) {
            BigDecimal juros = saldo.multiply(taxa, MC).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal amortizacao = parcelaFixa.subtract(juros).setScale(SCALE, RoundingMode.HALF_UP);

            if (i == n) {
                // Ultima parcela: residual no principal pra fechar soma com valorFinanciado.
                amortizacao = valorFinanciado.subtract(somaPrincipal).setScale(SCALE, RoundingMode.HALF_UP);
            }

            LocalDate vencimento = calcularVencimento(p, i);
            ParcelaCalculada parcela =
                    new ParcelaCalculada(i, amortizacao, juros, BigDecimal.ZERO, BigDecimal.ZERO, vencimento);
            parcelas.add(parcela);
            somaPrincipal = somaPrincipal.add(amortizacao);
            somaTotal = somaTotal.add(parcela.total());
            saldo = saldo.subtract(amortizacao);
        }

        return new ResultadoCalculo(parcelas, somaTotal);
    }

    private static BigDecimal calcularParcelaFixa(BigDecimal pv, BigDecimal taxa, int n) {
        if (taxa.signum() == 0) {
            return pv.divide(BigDecimal.valueOf(n), MC).setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal um = BigDecimal.ONE;
        BigDecimal umMaisI = um.add(taxa);
        BigDecimal umMaisIPotN = umMaisI.pow(n, MC);
        BigDecimal numerador = pv.multiply(taxa, MC).multiply(umMaisIPotN, MC);
        BigDecimal denominador = umMaisIPotN.subtract(um, MC);
        return numerador.divide(denominador, MC).setScale(SCALE, RoundingMode.HALF_UP);
    }

    static LocalDate calcularVencimento(ParametrosCalculo p, int numeroParcela) {
        long diasAcumulados = (long) p.primeiraParcelaDias() + (long) p.periodicidadeDias() * (numeroParcela - 1);
        return p.dataBase().plusDays(diasAcumulados);
    }
}
