package com.dynamis.sep_api.identity.application.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

public final class MfaChallengeInvalidoException extends ValidacaoException {

    private static final String CODIGO = "MFA-400-004";

    public MfaChallengeInvalidoException() {
        super(CODIGO, "Desafio MFA invalido ou expirado. Refaca o login.");
    }
}
