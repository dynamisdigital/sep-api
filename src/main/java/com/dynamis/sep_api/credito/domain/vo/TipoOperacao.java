package com.dynamis.sep_api.credito.domain.vo;

/**
 * Tipos de operacao de credito suportados na Sprint 8.
 *
 * <p>Sprint 8 cobre apenas capital de giro PJ + emprestimo PF generico ({@code OUTROS}). Linhas
 * dedicadas (consignado, antecipacao, etc.) ficam fora de escopo desta fase.
 */
public enum TipoOperacao {
    CAPITAL_GIRO,
    OUTROS
}
