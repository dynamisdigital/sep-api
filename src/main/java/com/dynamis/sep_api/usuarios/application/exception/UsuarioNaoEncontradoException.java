package com.dynamis.sep_api.usuarios.application.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

import java.util.UUID;

/**
 * Lancada quando um usuario referenciado por UUID nao existe. Mapeada para HTTP 404 pelo
 * {@code ApiExceptionHandler} via heranca de {@link RecursoNaoEncontradoException}.
 */
public final class UsuarioNaoEncontradoException extends RecursoNaoEncontradoException {

    private static final String CODIGO = "USR-404-001";

    public UsuarioNaoEncontradoException(UUID id) {
        super(CODIGO, "Usuario nao encontrado: " + id);
    }
}
