package com.dynamis.sep_api.credores.web.dto;

import com.dynamis.sep_api.credores.domain.vo.TipoCredora;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corpo de cadastro de empresa credora. {@code cnpj}/{@code razaoSocial} nao sao enviados:
 * derivam do onboarding PJ referenciado por {@code onboardingId}.
 */
public record CadastrarEmpresaCredoraRequest(
        @NotNull UUID onboardingId, @NotNull TipoCredora tipoCredora, @PositiveOrZero BigDecimal capacidadeAporte) {}
