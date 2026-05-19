package com.dynamis.sep_api.credito.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

/** Consentimento Open Finance nao encontrado (HTTP 404). */
public class ConsentimentoNaoEncontradoException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "CRD-404-002";

    public ConsentimentoNaoEncontradoException(String referencia) {
        super(CODIGO, "Consentimento Open Finance nao encontrado: " + referencia);
    }
}
