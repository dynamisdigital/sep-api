package com.dynamis.sep_api.contratos.domain.exception;

import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;

import java.util.UUID;

/** Contrato/versao buscado nao existe (HTTP 404). */
public class ContratoNaoEncontradoException extends RecursoNaoEncontradoException {

    public static final String CODIGO = "CTR-404-001";

    public ContratoNaoEncontradoException(String mensagem) {
        super(CODIGO, mensagem);
    }

    public static ContratoNaoEncontradoException porId(UUID id) {
        return new ContratoNaoEncontradoException("Contrato nao encontrado: " + id);
    }

    public static ContratoNaoEncontradoException porProposta(UUID propostaId) {
        return new ContratoNaoEncontradoException("Contrato para proposta nao encontrado: " + propostaId);
    }
}
