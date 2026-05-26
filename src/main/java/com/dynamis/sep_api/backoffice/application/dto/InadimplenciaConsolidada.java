package com.dynamis.sep_api.backoffice.application.dto;

import java.math.BigDecimal;

/**
 * Snapshot da inadimplencia total (Sprint 14 Task 14.5). {@code valorTotal} eh a soma do
 * {@code valor_devido} das parcelas em status {@code INADIMPLENTE}; {@code numeroParcelas} eh a
 * contagem dessas parcelas.
 */
public record InadimplenciaConsolidada(BigDecimal valorTotal, long numeroParcelas) {

    public static InadimplenciaConsolidada vazia() {
        return new InadimplenciaConsolidada(BigDecimal.ZERO, 0);
    }
}
