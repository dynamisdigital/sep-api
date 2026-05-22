package com.dynamis.sep_api.cobranca.application.service.calculo.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Resultado por parcela de uma execucao de {@code CalculadoraAmortizacao} (Sprint 12 Task 12.2).
 * {@code total = principal + juros + multa + encargos}, sempre em escala 2 HALF_UP.
 */
public record ParcelaCalculada(
        int numero,
        BigDecimal principal,
        BigDecimal juros,
        BigDecimal multa,
        BigDecimal encargos,
        LocalDate dataVencimento) {

    public ParcelaCalculada {
        if (numero <= 0) {
            throw new IllegalArgumentException("numero deve ser positivo");
        }
        principal = normalizar(principal, "principal");
        juros = normalizar(juros, "juros");
        multa = normalizar(multa, "multa");
        encargos = normalizar(encargos, "encargos");
        Objects.requireNonNull(dataVencimento, "dataVencimento obrigatoria");
    }

    public BigDecimal total() {
        return principal.add(juros).add(multa).add(encargos).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizar(BigDecimal valor, String campo) {
        Objects.requireNonNull(valor, campo + " obrigatorio");
        if (valor.signum() < 0) {
            throw new IllegalArgumentException(campo + " nao pode ser negativo");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
