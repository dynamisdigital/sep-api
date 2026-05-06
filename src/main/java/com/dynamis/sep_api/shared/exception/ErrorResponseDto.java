package com.dynamis.sep_api.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Payload padrao de erro da API SEP. Atende PRD §13 (Padrao de Erros da API).
 *
 * <p>Campo {@code traceId} e opcional e propagado do MDC quando presente (via
 * {@link com.dynamis.sep_api.shared.integration.CorrelationIdFilter}).
 */
@Schema(description = "Payload padrao de erro")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDto(
        @Schema(example = "2026-04-24T18:35:00-03:00") OffsetDateTime timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "password deve conter exatamente 6 caracteres") String message,
        @Schema(example = "/api/v1/usuarios") String path,
        @Schema(example = "8e1b8c5e-3f6f-4f5a-90c5-9b6c2c2b1c0a") String traceId) {

    public static ErrorResponseDto of(int status, String error, String message, String path, String traceId) {
        return new ErrorResponseDto(OffsetDateTime.now(), status, error, message, path, traceId);
    }
}
