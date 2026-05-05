package com.dynamis.sep_api.shared.exception;

public final class RecursoNaoEncontradoException extends DomainException {

    public RecursoNaoEncontradoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
