package com.dynamis.sep_api.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Payload padrao de erro da API SEP. Atende PRD §13 (Padrao de Erros da API).
 *
 * <p>Campo {@code traceId} e opcional e propagado do MDC quando presente (via
 * {@link com.dynamis.sep_api.shared.integration.CorrelationIdFilter}).
 *
 * <p>Campo {@code codigo} e opcional e identifica a condicao de erro do dominio (Sprint 36). So sai
 * no corpo quando o handler tem um identificador publicado para aquela condicao; handler sem codigo
 * continua entregando as seis propriedades de antes da sprint, sem {@code codigo: null}.
 */
@Schema(description = "Payload padrao de erro")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDto(
        @Schema(example = "2026-04-24T18:35:00-03:00") OffsetDateTime timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "password deve conter exatamente 6 caracteres") String message,
        @Schema(example = "/api/v1/usuarios") String path,
        @Schema(example = "8e1b8c5e-3f6f-4f5a-90c5-9b6c2c2b1c0a") String traceId,
        @Schema(
                        description = "Codigo estavel da condicao de erro. Ausente quando a condicao"
                                + " nao tem identificador publicado.",
                        example = "AUTH-423-001")
                String codigo) {

    /**
     * Corpo sem codigo. Preserva os call sites que antecedem a Sprint 36 — entre eles os quatro que
     * montam o corpo fora do {@code ApiExceptionHandler}, na cadeia do Spring Security.
     */
    public static ErrorResponseDto of(int status, String error, String message, String path, String traceId) {
        return of(status, error, message, path, traceId, null);
    }

    /** Corpo com codigo publicado. Unico caminho pelo qual a taxonomia de erro chega ao fio. */
    public static ErrorResponseDto of(
            int status, String error, String message, String path, String traceId, String codigo) {
        return new ErrorResponseDto(OffsetDateTime.now(), status, error, message, path, traceId, codigo);
    }
}
