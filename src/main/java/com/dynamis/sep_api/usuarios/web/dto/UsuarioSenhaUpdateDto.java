package com.dynamis.sep_api.usuarios.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioSenhaUpdateDto(
        @NotBlank String passwordAtual, @NotBlank @Size(min = 6, max = 6) String novaSenha) {}
