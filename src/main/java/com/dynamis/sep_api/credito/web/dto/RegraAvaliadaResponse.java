package com.dynamis.sep_api.credito.web.dto;

import com.dynamis.sep_api.credito.domain.vo.ResultadoRegra;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** Snapshot de regra avaliada pelo motor (Sprint 8). */
@Schema(description = "Regra de credito avaliada pelo motor — base auditavel")
public record RegraAvaliadaResponse(
        String nomeRegra, ResultadoRegra resultado, String motivo, boolean bloqueante, OffsetDateTime dataAvaliacao) {}
