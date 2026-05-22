package com.dynamis.sep_api.cobranca.application.service.calculo.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Saida de {@code CalculadoraAmortizacao}: lista ordenada de parcelas + total agregado da operacao.
 */
public record ResultadoCalculo(List<ParcelaCalculada> parcelas, BigDecimal valorTotal) {

    public ResultadoCalculo {
        Objects.requireNonNull(parcelas, "parcelas obrigatoria");
        Objects.requireNonNull(valorTotal, "valorTotal obrigatorio");
        if (parcelas.isEmpty()) {
            throw new IllegalArgumentException("resultado exige ao menos uma parcela");
        }
        parcelas = List.copyOf(parcelas);
        valorTotal = valorTotal.setScale(2, RoundingMode.HALF_UP);
    }
}
