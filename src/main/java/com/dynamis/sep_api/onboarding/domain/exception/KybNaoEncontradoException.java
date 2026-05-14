package com.dynamis.sep_api.onboarding.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

import java.util.UUID;

/** Agregado KYB da empresa inexistente para a solicitacao informada (HTTP 404). */
public class KybNaoEncontradoException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "ONB-404-002";

    public KybNaoEncontradoException(UUID solicitacaoId) {
        super(CODIGO, "KYB da solicitacao " + solicitacaoId + " nao encontrado");
    }
}
