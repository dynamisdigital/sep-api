package com.dynamis.sep_api.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Solicitacao de novo par access+refresh apresentando o refresh token atual.")
public record RefreshTokenRequestDto(@NotBlank @Schema(description = "Refresh token cru.") String refreshToken) {}
