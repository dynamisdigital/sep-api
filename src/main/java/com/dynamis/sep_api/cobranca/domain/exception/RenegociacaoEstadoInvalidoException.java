package com.dynamis.sep_api.cobranca.domain.exception;

import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import com.dynamis.sep_api.shared.exception.ConflitoException;

import java.util.UUID;

/**
 * Acao de transicao (aceitar/recusar) requisitada em renegociacao que ja saiu de {@code PROPOSTA}
 * — ja decidida ou expirada. HTTP 409 (mapeado pelo {@code ApiExceptionHandler} via
 * {@link ConflitoException}). Fix code review Task 13.7: antes esses casos lancavam
 * {@code IllegalStateException} (-> 500) e quebravam o contrato OpenAPI 409.
 */
public class RenegociacaoEstadoInvalidoException extends ConflitoException {

    public static final String CODIGO = "COB-409-003";

    public RenegociacaoEstadoInvalidoException(UUID renegociacaoId, StatusRenegociacao statusAtual, String acao) {
        super(
                CODIGO,
                "Renegociacao " + renegociacaoId + " esta em " + statusAtual + ", acao '" + acao + "' indisponivel");
    }

    public static RenegociacaoEstadoInvalidoException expirada(UUID renegociacaoId) {
        return new RenegociacaoEstadoInvalidoException(renegociacaoId, StatusRenegociacao.EXPIRADA, "decidir");
    }
}
