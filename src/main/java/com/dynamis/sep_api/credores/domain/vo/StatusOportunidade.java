package com.dynamis.sep_api.credores.domain.vo;

/**
 * Status de uma {@code OportunidadeInvestimento} (snapshot operacional de proposta/contrato
 * elegivel para captacao).
 *
 * <p>{@code DISPONIVEL}: aberta para manifestacao de interesse. {@code ENCERRADA}: nao aceita mais
 * interesse (ex.: operacao financiada associada). Sprint 17 nao faz matching financeiro — a
 * transicao para {@code ENCERRADA} e operacional, nao automatica por interesse.
 */
public enum StatusOportunidade {
    DISPONIVEL,
    ENCERRADA
}
