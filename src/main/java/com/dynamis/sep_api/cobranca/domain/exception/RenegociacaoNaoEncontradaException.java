package com.dynamis.sep_api.cobranca.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

import java.util.UUID;

/** Renegociacao buscada nao existe (HTTP 404). */
public class RenegociacaoNaoEncontradaException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "COB-404-003";

    public RenegociacaoNaoEncontradaException(UUID renegociacaoId) {
        super(CODIGO, "Renegociacao nao encontrada: " + renegociacaoId);
    }
}
