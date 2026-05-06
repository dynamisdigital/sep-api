package com.dynamis.sep_api.shared.exception;

/**
 * Excecao de dominio para falhas de validacao (HTTP 400). Marcada como {@code non-sealed} para
 * permitir subtipos por modulo, como {@code SenhaAtualIncorretaException}.
 */
public non-sealed class ValidacaoException extends DomainException {

    public ValidacaoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
