package com.dynamis.sep_api.usuarios.web.dto;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cadastro de usuario interno restrito a {@code POST /api/v1/admin/usuarios}. Introduzido no
 * follow-up 5F-FIX-01 da Sprint 5 para isolar a criacao de {@code Role.ADMIN} de qualquer
 * caminho publico.
 *
 * <p>Sprint 8 Task 8.4: aceita apenas {@code ADMIN} ou {@code CLIENTE}. {@code FINANCEIRO} deve
 * ser concedido somente via {@code POST /api/v1/usuarios/{id}/role}, que exige step-up e gera
 * audit log {@code ROLE_ALTERADO}.
 */
@Schema(
        description = "Cadastro autenticado de usuario interno (ADMIN ou CLIENTE) — restrito a ADMIN. "
                + "FINANCEIRO requer promocao via POST /api/v1/usuarios/{id}/role.")
public record UsuarioInternoCreateDto(
        @Schema(example = "operador@empresa.com") @NotBlank @Email String username,
        @Schema(example = "passphrase-do-operador", description = "Minimo 12 chars ou passphrase 4+ palavras") @NotBlank
                String password,
        @Schema(example = "ADMIN", description = "ADMIN ou CLIENTE — FINANCEIRO so via /usuarios/{id}/role") @NotNull
                Role role) {}
