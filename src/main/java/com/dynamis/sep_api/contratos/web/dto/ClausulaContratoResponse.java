package com.dynamis.sep_api.contratos.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Clausula de uma versao de contrato")
public record ClausulaContratoResponse(
        @Schema(example = "1f1d4920-3f55-6f48-9b3e-aa1234567890") UUID id,
        @Schema(example = "1") int ordem,
        @Schema(example = "OBJETO") String titulo,
        @Schema(example = "O presente contrato tem por objeto...") String texto) {}
