package com.dynamis.sep_api.shared.exception;

/**
 * Excecao de dominio para conflitos de estado (HTTP 409). Marcada como {@code non-sealed} para
 * permitir subtipos por modulo, como {@code UsernameJaExisteException}.
 */
public non-sealed class ConflitoException extends DomainException {

    public ConflitoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
