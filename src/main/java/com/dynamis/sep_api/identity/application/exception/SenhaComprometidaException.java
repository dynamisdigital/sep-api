package com.dynamis.sep_api.identity.application.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

public final class SenhaComprometidaException extends ValidacaoException {

    private static final String CODIGO = "AUTH-400-102";

    public SenhaComprometidaException() {
        super(CODIGO, "Esta senha aparece em vazamentos publicos conhecidos. Escolha outra.");
    }
}
