package com.dynamis.sep_api.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Conclui step-up apresentando codigo TOTP ou backup code.")
public record StepUpCompleteRequestDto(
        @NotNull @Schema(description = "Challenge emitido por /auth/step-up/initiate.") UUID stepUpChallengeId,
        @NotBlank @Schema(description = "Codigo TOTP de 6 digitos OU backup code.") String codigo) {}
