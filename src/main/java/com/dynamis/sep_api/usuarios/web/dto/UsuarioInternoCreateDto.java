package com.dynamis.sep_api.usuarios.web.dto;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cadastro de usuario interno (incluindo ADMIN) restrito a {@code POST /api/v1/admin/usuarios}.
 * Introduzido no follow-up 5F-FIX-01 da Sprint 5 para isolar a criacao de {@code Role.ADMIN} de
 * qualquer caminho publico.
 */
@Schema(description = "Cadastro autenticado de usuario interno (ADMIN ou CLIENTE) — restrito a ADMIN")
public record UsuarioInternoCreateDto(
        @Schema(example = "operador@empresa.com") @NotBlank @Email String username,
        @Schema(example = "passphrase-do-operador", description = "Minimo 12 chars ou passphrase 4+ palavras") @NotBlank
                String password,
        @Schema(example = "ADMIN", description = "ADMIN ou CLIENTE") @NotNull Role role) {}
