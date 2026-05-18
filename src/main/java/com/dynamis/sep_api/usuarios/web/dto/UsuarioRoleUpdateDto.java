package com.dynamis.sep_api.usuarios.web.dto;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Payload pra alterar role de um usuario (Sprint 8 Task 8.4). Restrito a ADMIN + step-up
 * authentication. ADMIN nao pode alterar a propria role.
 */
@Schema(description = "Alteracao de role de usuario — restrito a ADMIN + step-up")
public record UsuarioRoleUpdateDto(
        @Schema(example = "FINANCEIRO", description = "ADMIN, CLIENTE ou FINANCEIRO") @NotNull Role role) {}
