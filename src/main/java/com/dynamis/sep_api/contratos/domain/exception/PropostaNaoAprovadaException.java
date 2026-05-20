package com.dynamis.sep_api.contratos.domain.exception;

import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;

import java.util.UUID;

/**
 * Tentativa de gerar contrato para proposta que nao esta em {@link StatusProposta#APROVADA}. HTTP
 * 422. Sprint 10 Task 10.3 exige proposta aprovada como precondicao do fluxo de formalizacao.
 */
public class PropostaNaoAprovadaException extends OperacaoNaoProcessavelException {

    public static final String CODIGO = "CTR-422-003";

    public PropostaNaoAprovadaException(UUID propostaId, StatusProposta statusAtual) {
        super(CODIGO, "Proposta " + propostaId + " nao esta APROVADA (status atual: " + statusAtual + ")");
    }
}
