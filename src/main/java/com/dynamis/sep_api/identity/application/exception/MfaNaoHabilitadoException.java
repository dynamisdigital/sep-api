package com.dynamis.sep_api.identity.application.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

public final class MfaNaoHabilitadoException extends ValidacaoException {

    private static final String CODIGO = "MFA-400-003";

    public MfaNaoHabilitadoException() {
        super(CODIGO, "MFA TOTP nao esta habilitado para este usuario.");
    }
}
