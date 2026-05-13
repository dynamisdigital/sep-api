package com.dynamis.sep_api.onboarding.web.dto;

import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Metadados de documento enviado — incluido no GET de status. NUNCA expoe o binario. */
@Schema(description = "Metadados de um documento anexado a solicitacao")
public record DocumentoEnviadoResponse(
        UUID id,
        TipoDocumento tipo,
        OffsetDateTime dataEnvio,
        @Schema(example = "a1b2c3...64hex", description = "SHA-256 hex do conteudo") String sha256) {}
