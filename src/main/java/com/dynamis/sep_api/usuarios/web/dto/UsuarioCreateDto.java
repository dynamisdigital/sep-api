package com.dynamis.sep_api.usuarios.web.dto;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioCreateDto(
        @NotBlank @Email String username, @NotBlank @Size(min = 6, max = 6) String password, @NotNull Role role) {}
