package com.dynamis.sep_api.cobranca.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

import java.util.UUID;

/** Parcela buscada nao existe (HTTP 404). */
public class ParcelaCobrancaNaoEncontradaException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "COB-404-002";

    public ParcelaCobrancaNaoEncontradaException(String mensagem) {
        super(CODIGO, mensagem);
    }

    public static ParcelaCobrancaNaoEncontradaException porId(UUID parcelaId) {
        return new ParcelaCobrancaNaoEncontradaException("Parcela nao encontrada: " + parcelaId);
    }
}
