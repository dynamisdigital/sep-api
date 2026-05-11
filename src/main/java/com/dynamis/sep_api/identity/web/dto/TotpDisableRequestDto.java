package com.dynamis.sep_api.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Desabilitar TOTP exige senha atual (step-up token sera exigido na Task 5.6).")
public record TotpDisableRequestDto(@NotBlank @Schema(example = "senha-atual") String passwordAtual) {}
