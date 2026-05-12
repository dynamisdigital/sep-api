package com.dynamis.sep_api.usuarios.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Cadastro publico de usuario. A partir do follow-up 5F-FIX-01 (Sprint 5) o cadastro publico passa
 * a criar sempre {@code Role.CLIENTE}; o campo {@code role} foi removido para evitar escalacao de
 * privilegio via {@code POST /api/v1/usuarios}.
 *
 * <p>Promocao/criacao de {@code ADMIN} so ocorre via endpoint autenticado em {@code
 * POST /api/v1/admin/usuarios}.
 *
 * <p>Politica de senha (12+ chars ou passphrase 4+ palavras) e validada na camada application via
 * {@link com.dynamis.sep_api.identity.domain.vo.PasswordPolicy} (tambem checa HIBP).
 */
@Schema(description = "Dados para cadastro publico de usuario (sempre CLIENTE)")
public record UsuarioCreateDto(
        @Schema(example = "cliente@empresa.com") @NotBlank @Email String username,
        @Schema(example = "minha-passphrase-segura", description = "Minimo 12 chars ou passphrase 4+ palavras")
                @NotBlank
                String password) {}
