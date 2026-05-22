package com.dynamis.sep_api.cobranca.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

import java.util.UUID;

/** Agenda buscada nao existe (HTTP 404). */
public class AgendaPagamentoNaoEncontradaException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "COB-404-001";

    public AgendaPagamentoNaoEncontradaException(String mensagem) {
        super(CODIGO, mensagem);
    }

    public static AgendaPagamentoNaoEncontradaException porContrato(UUID contratoId) {
        return new AgendaPagamentoNaoEncontradaException("Agenda para contrato nao encontrada: " + contratoId);
    }
}
