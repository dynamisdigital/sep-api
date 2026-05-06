package com.dynamis.sep_api.usuarios.web.dto;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Dados publicos do usuario (sem senha)")
public record UsuarioResponseDto(
        @Schema(example = "1f0799c0-98b9-6d9d-bc4a-7d6f5b771001") UUID id,
        @Schema(example = "admin@empresa.com") String username,
        @Schema(example = "ADMIN") Role role,
        @Schema(example = "2026-04-24T18:30:00-03:00") OffsetDateTime dataCriacao,
        @Schema(example = "2026-04-24T18:30:00-03:00") OffsetDateTime dataModificacao,
        @Schema(example = "system") String criadoPor,
        @Schema(example = "system") String modificadoPor) {}
