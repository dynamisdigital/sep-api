package com.dynamis.sep_api.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * Payload padrao de erro da API SEP. Atende PRD §13 (Padrao de Erros da API).
 *
 * <p>Campo {@code traceId} e opcional e propagado do MDC quando presente (via
 * {@link com.dynamis.sep_api.shared.integration.CorrelationIdFilter}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDto(
        OffsetDateTime timestamp, int status, String error, String message, String path, String traceId) {

    public static ErrorResponseDto of(int status, String error, String message, String path, String traceId) {
        return new ErrorResponseDto(OffsetDateTime.now(), status, error, message, path, traceId);
    }
}
