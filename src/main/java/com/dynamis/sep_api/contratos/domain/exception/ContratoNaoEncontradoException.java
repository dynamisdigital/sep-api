package com.dynamis.sep_api.contratos.domain.exception;

import java.util.UUID;

/** Levantada quando contrato/versao buscada nao existe. Mapeada para 404 no handler global. */
public class ContratoNaoEncontradoException extends RuntimeException {

    public ContratoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }

    public static ContratoNaoEncontradoException porId(UUID id) {
        return new ContratoNaoEncontradoException("Contrato nao encontrado: " + id);
    }

    public static ContratoNaoEncontradoException porProposta(UUID propostaId) {
        return new ContratoNaoEncontradoException("Contrato para proposta nao encontrado: " + propostaId);
    }
}
