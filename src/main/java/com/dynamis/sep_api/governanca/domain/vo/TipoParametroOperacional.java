package com.dynamis.sep_api.governanca.domain.vo;

import java.math.BigDecimal;

/**
 * Tipo de um {@code ParametroOperacional} (Sprint 18). Define como o valor textual persistido e
 * validado e interpretado pelos consumidores.
 */
public enum TipoParametroOperacional {
    INTEGER,
    DECIMAL,
    BOOLEAN,
    STRING;

    /** {@code true} se {@code valor} e compativel com este tipo. */
    public boolean aceita(String valor) {
        if (valor == null) {
            return false;
        }
        return switch (this) {
            case INTEGER -> parseLong(valor);
            case DECIMAL -> parseDecimal(valor);
            case BOOLEAN -> "true".equalsIgnoreCase(valor) || "false".equalsIgnoreCase(valor);
            case STRING -> !valor.isBlank();
        };
    }

    private static boolean parseLong(String valor) {
        try {
            Long.parseLong(valor.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean parseDecimal(String valor) {
        try {
            new BigDecimal(valor.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
