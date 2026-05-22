package com.dynamis.sep_api.cobranca.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Payload de registro de recebimento manual (Sprint 12 Task 12.6). Header {@code Idempotency-Key}
 * obrigatorio na requisicao — validado pelo controller, nao por este record.
 */
@Schema(description = "Payload de registro de recebimento manual de parcela.")
public record RegistrarRecebimentoRequest(
        @NotNull @DecimalMin(value = "0.01") @Schema(example = "1000.00") BigDecimal valorRecebido,
        @NotNull @Schema(example = "2026-05-22T10:00:00-03:00") OffsetDateTime dataRecebimento,
        @NotBlank @Size(max = 40) @Schema(example = "TRANSFERENCIA") String meioPagamento,
        @Size(max = 100) @Schema(example = "comp-2026-05-22-001") String identificadorExterno,
        @Size(max = 500) @Schema(example = "Recebido via TED do tomador") String observacao) {}
