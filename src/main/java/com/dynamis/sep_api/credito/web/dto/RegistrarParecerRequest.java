package com.dynamis.sep_api.credito.web.dto;

import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload de registro de parecer manual (Sprint 8 Task 8.5). Restrito a {@code ROLE_FINANCEIRO}
 * + step-up token no controller.
 */
@Schema(description = "Parecer manual de operador FINANCEIRO sobre proposta de credito")
public record RegistrarParecerRequest(
        @Schema(example = "APROVAR", description = "APROVAR, REJEITAR ou PENDENCIA") @NotNull DecisaoParecer decisao,
        @Schema(example = "Cliente com bom historico interno", description = "Min 10, max 1000 chars")
                @NotBlank
                @Size(min = 10, max = 1000)
                String justificativa) {}
