package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

import java.util.UUID;

/** Oportunidade de investimento inexistente (HTTP 404). */
public class OportunidadeNaoEncontradaException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "CRD-404-002";

    public OportunidadeNaoEncontradaException(UUID id) {
        super(CODIGO, "Oportunidade " + id + " nao encontrada");
    }
}
