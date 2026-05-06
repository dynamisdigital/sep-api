package com.dynamis.sep_api.usuarios.web.dto;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para cadastro publico de usuario")
public record UsuarioCreateDto(
        @Schema(example = "admin@empresa.com") @NotBlank @Email String username,
        @Schema(example = "123456", minLength = 6, maxLength = 6) @NotBlank @Size(min = 6, max = 6) String password,
        @Schema(example = "ADMIN") @NotNull Role role) {}
