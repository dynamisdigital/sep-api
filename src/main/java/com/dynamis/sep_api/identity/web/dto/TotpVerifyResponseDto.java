package com.dynamis.sep_api.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado da verificacao TOTP. Na Task 5.3 evolui para devolver tokens.")
public record TotpVerifyResponseDto(
        @Schema(description = "true se TOTP ou backup code foram aceitos.") boolean verificado,
        @Schema(description = "true se o codigo apresentado era um backup code.") boolean usouBackupCode) {}
