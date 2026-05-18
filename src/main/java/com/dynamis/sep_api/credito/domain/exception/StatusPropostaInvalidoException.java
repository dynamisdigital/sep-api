package com.dynamis.sep_api.credito.domain.exception;

import com.dynamis.sep_api.credito.domain.vo.StatusProposta;

/** Transicao de status invalida em uma {@code PropostaCredito} (HTTP 400). */
public class StatusPropostaInvalidoException extends PropostaInvalidaException {

    public static final String CODIGO_TRANSICAO = "CRD-400-002";

    public StatusPropostaInvalidoException(String operacao, StatusProposta statusAtual) {
        super("Operacao '" + operacao + "' invalida no status " + statusAtual);
    }

    public StatusPropostaInvalidoException(String operacao, StatusProposta statusAtual, StatusProposta alvo) {
        super("Transicao invalida em proposta: '" + operacao + "' " + statusAtual + " -> " + alvo);
    }
}
