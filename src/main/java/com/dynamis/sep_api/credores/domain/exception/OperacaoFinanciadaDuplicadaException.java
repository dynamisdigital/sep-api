package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

/** Ja existe operacao financiada associando a credora ao contrato (HTTP 409). */
public class OperacaoFinanciadaDuplicadaException extends ConflitoException {

    public static final String CODIGO = "CRD-409-003";

    public OperacaoFinanciadaDuplicadaException() {
        super(CODIGO, "Credora ja possui operacao financiada para este contrato");
    }
}
