package com.dynamis.sep_api.identity.application.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

public final class TotpInvalidoException extends ValidacaoException {

    private static final String CODIGO = "MFA-400-002";

    public TotpInvalidoException() {
        super(CODIGO, "Codigo TOTP invalido ou expirado.");
    }
}
