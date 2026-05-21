package com.dynamis.sep_api.contratos.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Visao consolidada do contrato (versao vigente + aceite, quando houver)")
public record ContratoResponse(
        @Schema(example = "1f1d4920-3f55-6f48-9b3e-aa1234567890") UUID id,
        @Schema(description = "Proposta de credito associada (1:1)") UUID propostaId,
        @Schema(description = "Tomador titular") UUID tomadorId,
        @Schema(example = "MUTUO") String tipo,
        @Schema(example = "AGUARDANDO_ACEITE") String status,
        @Schema(description = "Versao mais recente do contrato; null se ainda nao gerada")
                VersaoContratoResponse versaoVigente,
        @Schema(description = "Aceite da versao vigente; null se ainda nao aceito") AceiteContratoResponse aceite,
        @Schema(example = "2026-05-20T15:30:00-03:00") OffsetDateTime dataCriacao,
        @Schema(example = "2026-05-20T15:30:00-03:00") OffsetDateTime dataModificacao) {}
