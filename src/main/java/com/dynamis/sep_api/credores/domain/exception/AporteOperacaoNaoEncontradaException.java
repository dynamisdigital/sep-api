package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

/**
 * Ausencia neutra (HTTP 404) da operacao financiada no contexto de aporte (Sprint 29). Operacao
 * inexistente e operacao de outra credora lancam esta mesma excecao generica, sem identificador —
 * mesma estrategia anti-enumeracao de {@link StatusPixOperacaoNaoEncontradoException}.
 */
public class AporteOperacaoNaoEncontradaException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "CRD-404-006";

    public AporteOperacaoNaoEncontradaException() {
        super(CODIGO, "Operacao nao encontrada para aporte");
    }
}
