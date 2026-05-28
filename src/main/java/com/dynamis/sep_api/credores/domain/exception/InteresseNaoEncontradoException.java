package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

/** Interesse ativo inexistente para a credora na oportunidade informada (HTTP 404). */
public class InteresseNaoEncontradoException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "CRD-404-003";

    public InteresseNaoEncontradoException() {
        super(CODIGO, "Nenhum interesse ativo encontrado para esta credora na oportunidade");
    }
}
