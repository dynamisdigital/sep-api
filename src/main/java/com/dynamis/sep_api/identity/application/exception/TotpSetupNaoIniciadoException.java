package com.dynamis.sep_api.identity.application.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

public final class TotpSetupNaoIniciadoException extends ValidacaoException {

    private static final String CODIGO = "MFA-400-001";

    public TotpSetupNaoIniciadoException() {
        super(CODIGO, "Nenhum setup de TOTP iniciado para este usuario. Execute /auth/totp/setup primeiro.");
    }
}
