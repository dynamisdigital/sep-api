package com.dynamis.sep_api.usuarios.application.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

public final class UsernameJaExisteException extends ConflitoException {

    private static final String CODIGO = "USR-409-001";

    public UsernameJaExisteException(String username) {
        super(CODIGO, "Ja existe usuario com username " + username);
    }
}
