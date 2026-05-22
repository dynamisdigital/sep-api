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
 * Amortizacao SAC (Sistema de Amortizacao Constante): amortizacao igual em todas as parcelas,
 * juros e parcela total decrescentes. Diferenca residual de arredondamento eh consolidada no
 * principal da ultima parcela.
 */
@Component
public class CalculadoraSAC implements CalculadoraAmortizacao {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int SCALE = 2;

    @Override
    public SistemaAmortizacao sistema() {
        return SistemaAmortizacao.SAC;
    }

    @Override
    public ResultadoCalculo calcular(ParametrosCalculo p) {
        BigDecimal valorFinanciado = p.valorFinanciado();
        BigDecimal taxa = p.taxaMensal();
        int n = p.numeroParcelas();

        BigDecimal amortizacaoBase =
                valorFinanciado.divide(BigDecimal.valueOf(n), MC).setScale(SCALE, RoundingMode.HALF_UP);

        List<ParcelaCalculada> parcelas = new ArrayList<>(n);
        BigDecimal saldo = valorFinanciado;
        BigDecimal somaPrincipal = BigDecimal.ZERO;
        BigDecimal somaTotal = BigDecimal.ZERO;

        for (int i = 1; i <= n; i++) {
            BigDecimal juros = saldo.multiply(taxa, MC).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal amortizacao = amortizacaoBase;

            if (i == n) {
                amortizacao = valorFinanciado.subtract(somaPrincipal).setScale(SCALE, RoundingMode.HALF_UP);
            }

            LocalDate vencimento = CalculadoraPrice.calcularVencimento(p, i);
            ParcelaCalculada parcela =
                    new ParcelaCalculada(i, amortizacao, juros, BigDecimal.ZERO, BigDecimal.ZERO, vencimento);
            parcelas.add(parcela);
            somaPrincipal = somaPrincipal.add(amortizacao);
            somaTotal = somaTotal.add(parcela.total());
            saldo = saldo.subtract(amortizacao);
        }

        return new ResultadoCalculo(parcelas, somaTotal);
    }
}
