package com.dynamis.sep_api.backoffice.domain.exception;

import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.shared.exception.ConflitoException;

import java.util.UUID;

/**
 * Transicao de estado nao permitida sobre {@code ItemFilaOperacional} (Sprint 14). Mapeada para
 * HTTP 409 pelo {@code ApiExceptionHandler} via {@link ConflitoException}.
 *
 * <p>Exemplos: assumir item ja em tratamento; resolver item ainda aberto; ignorar item ja final.
 */
public class TransicaoItemInvalidaException extends ConflitoException {

    public static final String CODIGO = "BOF-409-001";

    public TransicaoItemInvalidaException(UUID itemId, StatusItemFila atual, StatusItemFila alvo) {
        super(
                CODIGO,
                "Item " + itemId + " esta em " + atual + ", transicao para " + alvo + " indisponivel");
    }

    public TransicaoItemInvalidaException(StatusItemFila atual, StatusItemFila alvo) {
        super(CODIGO, "Item em " + atual + ", transicao para " + alvo + " indisponivel");
    }
}
