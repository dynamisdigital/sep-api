package com.dynamis.sep_api.pix.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** Request de solicitacao de desembolso Pix assistido (Sprint 20 Task 20.5). */
@Schema(description = "Solicitacao de desembolso Pix para um contrato elegivel")
public record SolicitarDesembolsoRequest(
        @Schema(description = "Contrato ASSINADO de origem", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                UUID contratoId,
        @Schema(
                        description = "Valor do desembolso; deve igualar o valor financiado do contrato",
                        example = "10000.00",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @Positive
                BigDecimal valor,
        @Schema(
                        description = "Chave Pix do destinatario (tomador). Nunca persistida em claro.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String chavePixDestino) {}
