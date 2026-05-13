package com.dynamis.sep_api.onboarding.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

/** CPF ja possui solicitacao de onboarding em status ativo (HTTP 409). */
public class CpfComOnboardingAtivoException extends ConflitoException {

    public static final String CODIGO = "ONB-409-001";

    public CpfComOnboardingAtivoException() {
        super(CODIGO, "CPF ja possui solicitacao de onboarding ativa");
    }
}
