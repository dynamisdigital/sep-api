package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

/** Ja existe interesse ATIVO da credora na oportunidade (HTTP 409). */
public class InteresseDuplicadoException extends ConflitoException {

    public static final String CODIGO = "CRD-409-002";

    public InteresseDuplicadoException() {
        super(CODIGO, "Credora ja possui interesse ativo nesta oportunidade");
    }
}
