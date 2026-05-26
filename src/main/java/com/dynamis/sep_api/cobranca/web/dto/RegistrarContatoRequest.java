package com.dynamis.sep_api.cobranca.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Payload pra registrar contato manual com o tomador (Sprint 13 Task 13.7). Apenas
 * {@code FINANCEIRO}/{@code ADMIN}. Nao altera status da parcela.
 */
@Schema(description = "Contato manual com o tomador registrado pelo financeiro/admin.")
public record RegistrarContatoRequest(
        @NotBlank
                @Size(max = 500)
                @Schema(
                        description = "Resumo do contato (no maximo 500 chars).",
                        example = "Cliente confirmou pagamento ate sexta-feira.")
                String descricao,
        @PositiveOrZero @Schema(description = "Dias de atraso no momento do contato (opcional).") Integer diasAtraso) {}
