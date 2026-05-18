package com.dynamis.sep_api.credito.web.dto;

import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** Snapshot do score interno gerado pelo motor (Sprint 8). */
@Schema(description = "Score interno produzido pelo motor de regras de credito")
public record ScoreInternoResponse(
        @Schema(example = "850") int valor,
        StatusProposta statusSugerido,
        int falhas,
        int pendencias,
        OffsetDateTime dataCalculo) {}
