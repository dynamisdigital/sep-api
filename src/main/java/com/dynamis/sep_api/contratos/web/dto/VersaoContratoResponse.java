package com.dynamis.sep_api.contratos.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Versao gerada de um contrato")
public record VersaoContratoResponse(
        @Schema(example = "1f1d4920-3f55-6f48-9b3e-aa1234567890") UUID id,
        @Schema(example = "1") int numero,
        @Schema(description = "Texto integral renderizado") String conteudoTexto,
        @Schema(description = "Hash SHA-256 hex lowercase do conteudo final", example = "ba78...") String hashSha256,
        @Schema(example = "2026-05-20T15:30:00-03:00") OffsetDateTime dataGeracao,
        @Schema(description = "Parecer que originou a versao (nulo quando regenerada manualmente)")
                UUID parecerOrigemId,
        @Schema(description = "Clausulas extraidas do template, em ordem") List<ClausulaContratoResponse> clausulas) {}
