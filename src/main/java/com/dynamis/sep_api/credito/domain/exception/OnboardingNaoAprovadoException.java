package com.dynamis.sep_api.credito.domain.exception;

import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;

/**
 * Onboarding referenciado pela proposta nao esta em {@code APROVADO_FINAL} (HTTP 422). Pre-condicao
 * de negocio para qualquer operacao de credito (CMN 4.656/2018 — KYC/PLD obrigatorios antes de
 * credito).
 */
public class OnboardingNaoAprovadoException extends OperacaoNaoProcessavelException {

    public static final String CODIGO = "CRD-422-001";

    public OnboardingNaoAprovadoException(StatusOnboarding statusAtual) {
        super(CODIGO, "Onboarding deve estar APROVADO_FINAL; atual: " + statusAtual);
    }
}
