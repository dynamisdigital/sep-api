package com.dynamis.sep_api.shared.exception;

/**
 * Excecao de dominio para recursos nao encontrados (HTTP 404). Marcada como {@code non-sealed}
 * para permitir subtipos por modulo, como {@code UsuarioNaoEncontradoException}.
 */
public non-sealed class RecursoNaoEncontradoException extends DomainException {

    public RecursoNaoEncontradoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
