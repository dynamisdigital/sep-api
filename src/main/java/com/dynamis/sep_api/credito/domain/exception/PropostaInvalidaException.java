package com.dynamis.sep_api.credito.domain.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

/** Estado/operacao invalida em uma {@code PropostaCredito} (HTTP 400). */
public class PropostaInvalidaException extends ValidacaoException {

    public static final String CODIGO = "CRD-400-001";

    public PropostaInvalidaException(String mensagem) {
        super(CODIGO, mensagem);
    }
}
