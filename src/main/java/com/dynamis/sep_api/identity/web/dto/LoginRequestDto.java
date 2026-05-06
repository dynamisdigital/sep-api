package com.dynamis.sep_api.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciais de login")
public record LoginRequestDto(
        @Schema(example = "admin@empresa.com") @NotBlank @Email String username,
        @Schema(example = "123456") @NotBlank String password) {}
