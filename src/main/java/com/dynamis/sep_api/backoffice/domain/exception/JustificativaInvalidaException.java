package com.dynamis.sep_api.backoffice.domain.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

/** Justificativa de resolucao/ignorar abaixo do minimo de 20 caracteres (HTTP 400). */
public class JustificativaInvalidaException extends ValidacaoException {

    public static final String CODIGO = "BOF-400-001";
    public static final int MIN_CARACTERES = 20;

    public JustificativaInvalidaException() {
        super(CODIGO, "Justificativa exige no minimo " + MIN_CARACTERES + " caracteres");
    }
}
