package com.dynamis.sep_api.cobranca.application.service.calculo.dto;

import com.dynamis.sep_api.cobranca.application.service.calculo.SistemaAmortizacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Parametros de entrada das calculadoras de amortizacao (Sprint 12 Task 12.2).
 *
 * <p>{@code valorFinanciado} > 0; {@code taxaMensal} em forma decimal (0.02 = 2% ao mes; aceita
 * zero); {@code numeroParcelas} > 0; {@code dataBase} a partir da qual a primeira parcela vence em
 * {@code primeiraParcelaDias} dias corridos; demais parcelas a cada {@code periodicidadeDias}.
 */
public record ParametrosCalculo(
        BigDecimal valorFinanciado,
        BigDecimal taxaMensal,
        int numeroParcelas,
        LocalDate dataBase,
        SistemaAmortizacao sistema,
        int primeiraParcelaDias,
        int periodicidadeDias) {

    public ParametrosCalculo {
        Objects.requireNonNull(valorFinanciado, "valorFinanciado obrigatorio");
        Objects.requireNonNull(taxaMensal, "taxaMensal obrigatoria");
        Objects.requireNonNull(dataBase, "dataBase obrigatoria");
        Objects.requireNonNull(sistema, "sistema obrigatorio");
        if (valorFinanciado.signum() <= 0) {
            throw new IllegalArgumentException("valorFinanciado deve ser positivo");
        }
        if (taxaMensal.signum() < 0) {
            throw new IllegalArgumentException("taxaMensal nao pode ser negativa");
        }
        if (numeroParcelas <= 0) {
            throw new IllegalArgumentException("numeroParcelas deve ser positivo");
        }
        if (primeiraParcelaDias <= 0) {
            throw new IllegalArgumentException("primeiraParcelaDias deve ser positivo");
        }
        if (periodicidadeDias <= 0) {
            throw new IllegalArgumentException("periodicidadeDias deve ser positivo");
        }
    }
}
