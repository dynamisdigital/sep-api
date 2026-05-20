package com.dynamis.sep_api.contratos.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Aceite registrado sobre uma versao de contrato")
public record AceiteContratoResponse(
        @Schema(example = "1f1d4920-3f55-6f48-9b3e-aa1234567890") UUID id,
        @Schema(description = "Versao aceita") UUID versaoId,
        @Schema(description = "Tomador que aceitou") UUID tomadorId,
        @Schema(example = "2026-05-20T15:35:00-03:00") OffsetDateTime dataAceite,
        @Schema(example = "203.0.113.42") String ipOrigem,
        @Schema(description = "User-Agent capturado (truncado em 500 chars)") String userAgentOrigem) {}
