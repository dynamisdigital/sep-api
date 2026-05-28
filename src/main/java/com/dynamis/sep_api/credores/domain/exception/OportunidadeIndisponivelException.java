package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;

/** Oportunidade nao esta DISPONIVEL para manifestacao de interesse (HTTP 422). */
public class OportunidadeIndisponivelException extends OperacaoNaoProcessavelException {

    public static final String CODIGO = "CRD-422-003";

    public OportunidadeIndisponivelException() {
        super(CODIGO, "Oportunidade nao esta disponivel para interesse");
    }
}
