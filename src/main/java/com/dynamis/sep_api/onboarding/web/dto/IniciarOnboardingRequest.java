package com.dynamis.sep_api.onboarding.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/** Request body para iniciar onboarding KYC PF. */
@Schema(description = "Dados minimos para abrir solicitacao de onboarding KYC PF")
public record IniciarOnboardingRequest(
        @Schema(example = "52998224725", description = "CPF com ou sem mascara") @NotBlank String cpf,
        @Schema(example = "Joao da Silva") @NotBlank String nomeCompleto,
        @Schema(example = "1990-01-15") @NotNull @Past LocalDate dataNascimento) {}
