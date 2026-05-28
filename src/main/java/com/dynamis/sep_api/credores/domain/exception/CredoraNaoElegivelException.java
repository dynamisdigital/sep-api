package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;

/** Credora nao esta ATIVA + ELEGIVEL para manifestar interesse (HTTP 422). */
public class CredoraNaoElegivelException extends OperacaoNaoProcessavelException {

    public static final String CODIGO = "CRD-422-002";

    public CredoraNaoElegivelException() {
        super(CODIGO, "Credora precisa estar ATIVA e ELEGIVEL para manifestar interesse");
    }
}
