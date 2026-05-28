package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;

/** Contrato informado nao existe ou nao e elegivel para associacao a carteira (HTTP 422). */
public class ContratoNaoElegivelException extends OperacaoNaoProcessavelException {

    public static final String CODIGO = "CRD-422-004";

    public ContratoNaoElegivelException() {
        super(CODIGO, "Contrato informado nao existe ou nao e elegivel para associacao");
    }
}
