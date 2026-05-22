package com.dynamis.sep_api.cobranca.domain.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Composicao de valores de uma {@code ParcelaCobranca}: principal + juros + multa + encargos. O
 * total e derivado da soma dos quatro componentes, sempre com escala 2 ({@code HALF_UP}).
 *
 * <p>Sprint 12: encargos default zero. Multa/juros zero pra parcelas {@code PENDENTE} sem atraso;
 * atualizadas em {@code CalcularValorAtualizadoParcelaUseCase} (Task 12.5) quando ha mora.
 *
 * <p>VO imutavel persistido nas colunas {@code principal}, {@code juros}, {@code multa},
 * {@code encargos} de {@code parcela_cobranca}; {@code total} fica derivado e nao ocupa coluna.
 */
public record ComposicaoValor(BigDecimal principal, BigDecimal juros, BigDecimal multa, BigDecimal encargos) {

    public ComposicaoValor {
        principal = normalizar("principal", principal, false);
        juros = normalizar("juros", juros, true);
        multa = normalizar("multa", multa, true);
        encargos = normalizar("encargos", encargos, true);
    }

    public BigDecimal total() {
        return principal.add(juros).add(multa).add(encargos).setScale(2, RoundingMode.HALF_UP);
    }

    public static ComposicaoValor principalApenas(BigDecimal principal) {
        return new ComposicaoValor(principal, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public static ComposicaoValor de(BigDecimal principal, BigDecimal juros, BigDecimal multa, BigDecimal encargos) {
        return new ComposicaoValor(principal, juros, multa, encargos);
    }

    private static BigDecimal normalizar(String campo, BigDecimal valor, boolean zeroPermitido) {
        Objects.requireNonNull(valor, campo + " obrigatorio");
        if (valor.signum() < 0) {
            throw new IllegalArgumentException(campo + " nao pode ser negativo");
        }
        if (!zeroPermitido && valor.signum() == 0) {
            throw new IllegalArgumentException(campo + " deve ser positivo");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
