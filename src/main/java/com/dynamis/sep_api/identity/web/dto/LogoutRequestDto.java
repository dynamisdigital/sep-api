package com.dynamis.sep_api.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Revoga o refresh token informado (logout do dispositivo atual).")
public record LogoutRequestDto(@NotBlank @Schema(description = "Refresh token cru.") String refreshToken) {}
