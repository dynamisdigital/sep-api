package com.dynamis.sep_api.onboarding.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

/** CNPJ ja possui solicitacao de onboarding KYB em status ativo (HTTP 409). */
public class CnpjComOnboardingAtivoException extends ConflitoException {

    public static final String CODIGO = "ONB-409-002";

    public CnpjComOnboardingAtivoException() {
        super(CODIGO, "CNPJ ja possui solicitacao de onboarding ativa");
    }
}
