package com.dynamis.sep_api.identity.application.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

public final class SenhaFracaException extends ValidacaoException {

    private static final String CODIGO = "AUTH-400-101";

    public SenhaFracaException(String detalhe) {
        super(CODIGO, "Senha nao atende a politica de seguranca: " + detalhe);
    }
}
