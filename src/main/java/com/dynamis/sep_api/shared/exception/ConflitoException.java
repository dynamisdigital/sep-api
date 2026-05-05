package com.dynamis.sep_api.shared.exception;

public final class ConflitoException extends DomainException {

    public ConflitoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
