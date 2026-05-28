package com.dynamis.sep_api.credores.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Corpo da associacao operacional assistida (admin). Informar {@code contratoId} ou
 * {@code oportunidadeId} (pelo menos um); quando ambos, devem ser coerentes.
 */
public record AssociarOperacaoRequest(
        @NotNull UUID empresaCredoraId,
        UUID contratoId,
        UUID oportunidadeId,
        @NotBlank @Size(max = 500) String justificativa) {}
