package com.dynamis.sep_api.cobranca.application.service.calculo;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Multa moratoria one-shot sobre o valor em atraso (Sprint 12 Task 12.2.3). Default 2% conforme
 * {@code app.cobranca.multa-atraso} (limite legal CDC Lei 8.078/1990 §52).
 *
 * <p>Formula: {@code multa = valorBase * percentualMulta}. Quando {@code dataReferencia <=
 * dataVencimento}, retorna zero.
 */
@Component
public class CalculadoraMulta {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int SCALE = 2;

    public BigDecimal calcular(
            BigDecimal valorBase, LocalDate dataVencimento, LocalDate dataReferencia, BigDecimal percentualMulta) {
        Objects.requireNonNull(valorBase, "valorBase obrigatorio");
        Objects.requireNonNull(dataVencimento, "dataVencimento obrigatoria");
        Objects.requireNonNull(dataReferencia, "dataReferencia obrigatoria");
        Objects.requireNonNull(percentualMulta, "percentualMulta obrigatorio");
        if (valorBase.signum() < 0) {
            throw new IllegalArgumentException("valorBase nao pode ser negativo");
        }
        if (percentualMulta.signum() < 0) {
            throw new IllegalArgumentException("percentualMulta nao pode ser negativo");
        }
        if (!dataReferencia.isAfter(dataVencimento)) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return valorBase.multiply(percentualMulta, MC).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
