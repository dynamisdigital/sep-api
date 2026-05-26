package com.dynamis.sep_api.backoffice.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Anti-abuso de reprocesso: mais de 3 reprocessos por entidade em 24h (Sprint 14 Task 14.4).
 * Mapeada para HTTP 429 via {@code @ResponseStatus}. Task 14.7 podera adicionar
 * {@code @ExceptionHandler} dedicado pra emitir o payload padrao {@code ErrorResponseDto}.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class LimiteReprocessoExcedidoException extends RuntimeException {

    public static final String CODIGO = "BOF-429-001";
    public static final int LIMITE_24H = 3;

    public LimiteReprocessoExcedidoException(String identificadorExterno) {
        super("Limite de " + LIMITE_24H + " reprocessos por entidade em 24h excedido (" + identificadorExterno + ")");
    }
}
