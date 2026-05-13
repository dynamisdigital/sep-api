package com.dynamis.sep_api.onboarding.domain.exception;

import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.shared.exception.ValidacaoException;

/** Transicao de status invalida ou operacao incompativel com status atual (HTTP 400). */
public class StatusOnboardingInvalidoException extends ValidacaoException {

    public static final String CODIGO = "ONB-400-001";

    public StatusOnboardingInvalidoException(String operacao, StatusOnboarding statusAtual) {
        super(CODIGO, "Operacao '" + operacao + "' invalida no status " + statusAtual);
    }

    public StatusOnboardingInvalidoException(String mensagem) {
        super(CODIGO, mensagem);
    }
}
