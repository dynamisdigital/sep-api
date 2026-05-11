package com.dynamis.sep_api.usuarios.web.dto;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Sprint 5 Task 5.5: politica de senha (12+ chars ou passphrase 4+ palavras) e validada na camada
 * de application via {@link com.dynamis.sep_api.identity.domain.vo.PasswordPolicy}, nao por Bean
 * Validation, para tambem checar vazamentos (HIBP).
 */
@Schema(description = "Dados para cadastro publico de usuario")
public record UsuarioCreateDto(
        @Schema(example = "admin@empresa.com") @NotBlank @Email String username,
        @Schema(example = "minha-passphrase-segura", description = "Minimo 12 chars ou passphrase 4+ palavras")
                @NotBlank
                String password,
        @Schema(example = "ADMIN") @NotNull Role role) {}
