package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

/**
 * Ausencia neutra (HTTP 404) da sugestao de matching (Sprint 30). Sugestao inexistente e sugestao
 * fora do escopo do operador lancam esta mesma excecao generica, sem identificador — estrategia
 * anti-enumeracao de {@link AporteOperacaoNaoEncontradaException}.
 */
public class MatchingNaoEncontradoException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "CRD-404-008";

    public MatchingNaoEncontradoException() {
        super(CODIGO, "Sugestao de matching nao encontrada");
    }
}
