package com.dynamis.sep_api.usuarios.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Alteracao da propria senha")
public record UsuarioSenhaUpdateDto(
        @Schema(example = "123456") @NotBlank String passwordAtual,
        @Schema(example = "654321", minLength = 6, maxLength = 6) @NotBlank @Size(min = 6, max = 6) String novaSenha) {}
