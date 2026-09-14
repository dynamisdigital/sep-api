package com.dynamis.sep_api.notificacao.application.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

/** Pagina ou tamanho de pagina fora dos limites da central (HTTP 400, ADR 0021 §9). */
public class PaginacaoInvalidaException extends ValidacaoException {

    public PaginacaoInvalidaException() {
        super("NTF-400-001", "Paginacao invalida: page deve ser maior ou igual a 0 e size entre 1 e 100");
    }
}
