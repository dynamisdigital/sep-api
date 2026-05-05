package com.dynamis.sep_api.usuarios.application.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

/**
 * Lancada quando o {@code passwordAtual} informado na alteracao de senha nao bate com o hash
 * armazenado. Mapeada para HTTP 400 pelo {@code ApiExceptionHandler} via heranca de
 * {@link ValidacaoException}.
 */
public final class SenhaAtualIncorretaException extends ValidacaoException {

    private static final String CODIGO = "USR-400-001";

    public SenhaAtualIncorretaException() {
        super(CODIGO, "Senha atual incorreta");
    }
}
