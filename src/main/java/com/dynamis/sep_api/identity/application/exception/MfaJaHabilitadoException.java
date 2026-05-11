package com.dynamis.sep_api.identity.application.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

public final class MfaJaHabilitadoException extends ConflitoException {

    private static final String CODIGO = "MFA-409-001";

    public MfaJaHabilitadoException() {
        super(CODIGO, "MFA TOTP ja esta habilitado para este usuario.");
    }
}
