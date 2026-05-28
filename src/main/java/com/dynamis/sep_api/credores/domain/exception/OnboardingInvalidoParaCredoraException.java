package com.dynamis.sep_api.credores.domain.exception;

import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;

/**
 * Onboarding referenciado nao habilita cadastro de credora (HTTP 422): nao e PJ ou ainda nao tem
 * dados de KYB (CNPJ) registrados.
 */
public class OnboardingInvalidoParaCredoraException extends OperacaoNaoProcessavelException {

    public static final String CODIGO = "CRD-422-001";

    private OnboardingInvalidoParaCredoraException(String mensagem) {
        super(CODIGO, mensagem);
    }

    public static OnboardingInvalidoParaCredoraException naoEmpresa() {
        return new OnboardingInvalidoParaCredoraException("Onboarding informado nao e de pessoa juridica");
    }

    public static OnboardingInvalidoParaCredoraException kybIncompleto() {
        return new OnboardingInvalidoParaCredoraException("Onboarding PJ ainda nao possui dados de KYB (CNPJ)");
    }
}
