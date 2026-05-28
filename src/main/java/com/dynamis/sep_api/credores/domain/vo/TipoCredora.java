package com.dynamis.sep_api.credores.domain.vo;

/**
 * Natureza operacional da empresa credora, registrada no {@code PerfilCredora}.
 *
 * <p>{@code EMPRESA} cobre pessoas juridicas comuns que aportam recursos; {@code
 * INSTITUICAO_FINANCEIRA} distingue participantes regulados, que podem ter parametros
 * operacionais diferentes nas sprints seguintes.
 */
public enum TipoCredora {
    EMPRESA,
    INSTITUICAO_FINANCEIRA
}
