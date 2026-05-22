package com.dynamis.sep_api.cobranca.domain.exception;

import com.dynamis.sep_api.shared.exception.ConflitoException;

/**
 * Reapresentacao de {@code Idempotency-Key} com payload divergente do recebimento original (Sprint
 * 12 Task 12.4 fix code review manual). Retornar a entrada antiga mascararia abuso ou erro de
 * cliente; HTTP 409.
 */
public class ChaveIdempotenciaConflitanteException extends ConflitoException {

    public static final String CODIGO = "COB-409-002";

    public ChaveIdempotenciaConflitanteException(String mensagem) {
        super(CODIGO, mensagem);
    }
}
