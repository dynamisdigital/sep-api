package com.dynamis.sep_api.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Primeiro codigo TOTP gerado, confirmando que o usuario possui o secret.")
public record TotpConfirmRequestDto(
        @NotBlank
                @Pattern(regexp = "\\d{6}", message = "codigo deve ter exatamente 6 digitos")
                @Schema(example = "123456")
                String codigo) {}
