package com.dynamis.sep_api.shared.exception;

public final class ValidacaoException extends DomainException {

    public ValidacaoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
