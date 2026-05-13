package com.dynamis.sep_api.onboarding.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

import java.util.UUID;

/** Solicitacao de onboarding inexistente (HTTP 404). */
public class OnboardingNaoEncontradoException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "ONB-404-001";

    public OnboardingNaoEncontradoException(UUID id) {
        super(CODIGO, "Solicitacao de onboarding " + id + " nao encontrada");
    }
}
