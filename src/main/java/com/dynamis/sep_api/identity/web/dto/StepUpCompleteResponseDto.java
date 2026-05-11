package com.dynamis.sep_api.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Step-up token cru. Enviar em X-Step-Up-Token na operacao sensivel.")
public record StepUpCompleteResponseDto(String stepUpToken) {}
