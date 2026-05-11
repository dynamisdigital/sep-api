package com.dynamis.sep_api.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Challenge para reautenticacao step-up; valido por 5 minutos.")
public record StepUpInitiateResponseDto(UUID stepUpChallengeId) {}
