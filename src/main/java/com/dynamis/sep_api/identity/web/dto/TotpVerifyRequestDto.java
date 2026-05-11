package com.dynamis.sep_api.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Verificacao do desafio TOTP no login (codigo TOTP ou backup code).")
public record TotpVerifyRequestDto(
        @NotNull @Schema(description = "UUID do usuario que esta logando.") UUID usuarioId,
        @NotBlank @Schema(description = "Codigo TOTP de 6 digitos OU backup code de 8 caracteres alfanumericos.")
                String codigo) {}
