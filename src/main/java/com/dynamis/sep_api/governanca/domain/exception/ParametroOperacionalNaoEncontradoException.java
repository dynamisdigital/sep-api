package com.dynamis.sep_api.governanca.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

/** Parametro operacional inexistente para a chave informada (HTTP 404). */
public class ParametroOperacionalNaoEncontradoException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "GOV-404-001";

    public ParametroOperacionalNaoEncontradoException(String chave) {
        super(CODIGO, "Parametro operacional '" + chave + "' nao encontrado");
    }
}
