package com.dynamis.sep_api.cobranca.domain.exception;

import java.util.UUID;

public class RenegociacaoNaoEncontradaException extends RuntimeException {

    public RenegociacaoNaoEncontradaException(UUID renegociacaoId) {
        super("renegociacao nao encontrada: " + renegociacaoId);
    }
}
