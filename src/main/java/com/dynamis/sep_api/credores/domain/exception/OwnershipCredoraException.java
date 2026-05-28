package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.AcessoNegadoException;

/** Onboarding informado pertence a outro usuario (HTTP 403). */
public class OwnershipCredoraException extends AcessoNegadoException {

    public static final String CODIGO = "CRD-403-001";

    public OwnershipCredoraException() {
        super(CODIGO, "Onboarding informado pertence a outro usuario");
    }
}
