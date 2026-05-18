package com.dynamis.sep_api.credito.domain.vo;

/**
 * Decisao registrada por operador financeiro em um {@code ParecerCredito} (Sprint 8 Task 8.4).
 *
 * <p>{@link #APROVAR} e {@link #REJEITAR} sao terminais e transicionam a proposta para estado
 * final. {@link #PENDENCIA} mantem a proposta aberta aguardando informacao adicional.
 */
public enum DecisaoParecer {
    APROVAR,
    REJEITAR,
    PENDENCIA
}
