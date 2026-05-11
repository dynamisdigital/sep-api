package com.dynamis.sep_api.usuarios.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Alteracao da propria senha. Politica avaliada em PasswordPolicy (Sprint 5).")
public record UsuarioSenhaUpdateDto(
        @Schema(example = "senha-atual-aqui") @NotBlank String passwordAtual,
        @Schema(example = "nova-passphrase-segura") @NotBlank String novaSenha) {}
