package com.dynamis.sep_api.onboarding.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Representacao publica de um representante legal. CPF SEMPRE mascarado nesta camada. */
@Schema(description = "Representante legal vinculado a uma KYB PJ")
public record RepresentanteLegalResponse(
        UUID id,
        @Schema(example = "Joao da Silva") String nome,
        @Schema(example = "529****4725", description = "CPF mascarado (3 primeiros + 2 ultimos)") String cpfMascarado,
        @Schema(example = "Administrador") String cargo,
        @Schema(description = "Resumo publico do PLD para este representante") ConsultaPldResumoResponse pld) {}
