package com.dynamis.sep_api.credores.domain.vo;

/**
 * Estados do aporte da credora em operacao financiada (Sprint 29 — Epic 15).
 *
 * <p>Fluxo esperado: {@code PENDENTE -> EM_PROCESSAMENTO -> LIQUIDADO}, com {@code FALHOU} a
 * partir de {@code PENDENTE} (registro recusado pelo provider) ou de {@code EM_PROCESSAMENTO}
 * (falha na liquidacao). {@code LIQUIDADO} e {@code FALHOU} sao terminais.
 */
public enum StatusAporteCredora {
    PENDENTE,
    EM_PROCESSAMENTO,
    LIQUIDADO,
    FALHOU
}
