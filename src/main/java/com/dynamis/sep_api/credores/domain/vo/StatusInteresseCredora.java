package com.dynamis.sep_api.credores.domain.vo;

/**
 * Status da manifestacao de interesse de uma credora numa oportunidade.
 *
 * <p>{@code ATIVO}: interesse vigente (unico por credora+oportunidade). {@code CANCELADO}:
 * retirado pela credora. Cancelamento e idempotente.
 */
public enum StatusInteresseCredora {
    ATIVO,
    CANCELADO
}
