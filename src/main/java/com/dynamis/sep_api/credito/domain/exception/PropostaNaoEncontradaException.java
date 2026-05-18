package com.dynamis.sep_api.credito.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

import java.util.UUID;

/** Proposta nao encontrada (HTTP 404). */
public class PropostaNaoEncontradaException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "CRD-404-001";

    public PropostaNaoEncontradaException(UUID propostaId) {
        super(CODIGO, "Proposta de credito " + propostaId + " nao encontrada");
    }
}
