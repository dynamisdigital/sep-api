package com.dynamis.sep_api.governanca.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de alteracao de parametro operacional (admin + step-up). */
public record AlterarParametroRequest(
        @NotBlank @Size(max = 500) String novoValor, @NotBlank @Size(max = 500) String justificativa) {}
