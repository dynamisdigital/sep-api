package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

import java.util.UUID;

/** Operacao financiada inexistente para a credora (HTTP 404). */
public class OperacaoFinanciadaNaoEncontradaException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "CRD-404-004";

    public OperacaoFinanciadaNaoEncontradaException(UUID id) {
        super(CODIGO, "Operacao financiada " + id + " nao encontrada");
    }
}
