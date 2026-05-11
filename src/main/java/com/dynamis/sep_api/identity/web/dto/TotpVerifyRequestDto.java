package com.dynamis.sep_api.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Verificacao do desafio TOTP no login. Recebe o mfaChallengeId emitido pelo /auth/login.")
public record TotpVerifyRequestDto(
        @NotNull @Schema(description = "Challenge emitido pelo /auth/login quando MFA esta ATIVO.") UUID mfaChallengeId,
        @NotBlank @Schema(description = "Codigo TOTP de 6 digitos OU backup code de 8 caracteres alfanumericos.")
                String codigo) {}
