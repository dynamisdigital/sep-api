package com.dynamis.sep_api.credito.domain.exception;

import com.dynamis.sep_api.shared.exception.ValidacaoException;

/**
 * Dado invalido em uma proposta ou parecer de credito (HTTP 400): o cliente corrige o que enviou.
 * Operacao que o status da proposta recusa e outra condicao — {@link StatusPropostaInvalidoException}.
 */
public class PropostaInvalidaException extends ValidacaoException {

    public static final String CODIGO = "PRP-400-001";

    public PropostaInvalidaException(String mensagem) {
        super(CODIGO, mensagem);
    }

    /** Para o subtipo que nomeia outra condicao e carrega o proprio codigo. */
    protected PropostaInvalidaException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
