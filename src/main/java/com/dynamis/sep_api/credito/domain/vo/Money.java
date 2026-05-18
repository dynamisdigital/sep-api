package com.dynamis.sep_api.credito.domain.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Valor monetario com escala 2 e moeda explicita. Moeda default {@code BRL} (real brasileiro).
 *
 * <p>Sempre usar {@code BigDecimal} para valores monetarios — nunca {@code double}. Construtor
 * valida valor positivo e normaliza para escala 2 (half-up). Para zero ou negativo, lanca
 * {@link IllegalArgumentException}.
 */
public record Money(BigDecimal valor, String moeda) {

    public static final String MOEDA_DEFAULT = "BRL";

    public Money {
        Objects.requireNonNull(valor, "valor obrigatorio");
        Objects.requireNonNull(moeda, "moeda obrigatoria");
        if (moeda.isBlank()) {
            throw new IllegalArgumentException("moeda nao pode ser vazia");
        }
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("valor monetario deve ser positivo");
        }
        valor = valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money brl(BigDecimal valor) {
        return new Money(valor, MOEDA_DEFAULT);
    }

    public static Money brl(String valor) {
        return brl(new BigDecimal(valor));
    }
}
