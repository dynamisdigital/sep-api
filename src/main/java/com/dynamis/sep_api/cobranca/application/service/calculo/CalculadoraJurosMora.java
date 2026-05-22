package com.dynamis.sep_api.cobranca.application.service.calculo;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Juros de mora pro rata die sobre o valor em atraso (Sprint 12 Task 12.2.3). Default 1% ao mes
 * conforme {@code app.cobranca.juros-mora-mensal}; ajustes por contrato no futuro.
 *
 * <p>Formula: {@code juros = valorBase * taxaMensal * diasAtraso / 30}. Quando {@code
 * dataReferencia <= dataVencimento}, retorna zero.
 */
@Component
public class CalculadoraJurosMora {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal DIAS_MES = new BigDecimal("30");
    private static final int SCALE = 2;

    public BigDecimal calcular(
            BigDecimal valorBase, LocalDate dataVencimento, LocalDate dataReferencia, BigDecimal taxaMensal) {
        Objects.requireNonNull(valorBase, "valorBase obrigatorio");
        Objects.requireNonNull(dataVencimento, "dataVencimento obrigatoria");
        Objects.requireNonNull(dataReferencia, "dataReferencia obrigatoria");
        Objects.requireNonNull(taxaMensal, "taxaMensal obrigatoria");
        if (valorBase.signum() < 0) {
            throw new IllegalArgumentException("valorBase nao pode ser negativo");
        }
        if (taxaMensal.signum() < 0) {
            throw new IllegalArgumentException("taxaMensal nao pode ser negativa");
        }
        if (!dataReferencia.isAfter(dataVencimento)) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        long diasAtraso = ChronoUnit.DAYS.between(dataVencimento, dataReferencia);
        return valorBase
                .multiply(taxaMensal, MC)
                .multiply(BigDecimal.valueOf(diasAtraso), MC)
                .divide(DIAS_MES, MC)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }
}
