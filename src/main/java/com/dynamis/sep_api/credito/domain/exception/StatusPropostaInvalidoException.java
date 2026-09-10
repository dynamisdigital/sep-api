package com.dynamis.sep_api.credito.domain.exception;

import com.dynamis.sep_api.credito.domain.vo.StatusProposta;

/**
 * Operacao que o status atual da {@code PropostaCredito} nao admite (HTTP 400).
 *
 * <p>Condicao distinta da {@link PropostaInvalidaException} de onde herda: la o cliente corrige o
 * dado enviado; aqui nenhum dado corrige, porque e o estado da proposta que recusa a operacao (ADR
 * 0020 §3). Ate a Sprint 37 os construtores nao passavam {@link #CODIGO_TRANSICAO}, e a transicao
 * saia com o codigo do pai.
 */
public class StatusPropostaInvalidoException extends PropostaInvalidaException {

    public static final String CODIGO_TRANSICAO = "PRP-400-002";

    public StatusPropostaInvalidoException(String operacao, StatusProposta statusAtual) {
        super(CODIGO_TRANSICAO, "Operacao '" + operacao + "' invalida no status " + statusAtual);
    }

    public StatusPropostaInvalidoException(String operacao, StatusProposta statusAtual, StatusProposta alvo) {
        super(CODIGO_TRANSICAO, "Transicao invalida em proposta: '" + operacao + "' " + statusAtual + " -> " + alvo);
    }

    private StatusPropostaInvalidoException(String mensagem) {
        super(CODIGO_TRANSICAO, mensagem);
    }

    /** Proposta em estado final nao aceita novo parecer; a mensagem e a que o endpoint ja devolvia. */
    public static StatusPropostaInvalidoException novoParecerEmEstadoFinal(StatusProposta statusAtual) {
        return new StatusPropostaInvalidoException(
                "Proposta ja esta em estado final " + statusAtual + "; novo parecer nao permitido");
    }
}
