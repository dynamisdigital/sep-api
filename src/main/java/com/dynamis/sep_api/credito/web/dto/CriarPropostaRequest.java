package com.dynamis.sep_api.credito.web.dto;

import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload de criacao de proposta de credito (Sprint 8 Task 8.5). {@code valorSolicitado} em BRL
 * (moeda fixa nesta fase). Validacoes de pre-condicao de negocio (onboarding APROVADO_FINAL +
 * ownership) sao aplicadas no {@code CriarPropostaCreditoUseCase}.
 */
@Schema(description = "Criacao de proposta de credito")
public record CriarPropostaRequest(
        @Schema(description = "UUID da solicitacao de onboarding aprovada do tomador") @NotNull
                UUID solicitacaoOnboardingId,
        @Schema(example = "CAPITAL_GIRO", description = "CAPITAL_GIRO ou OUTROS") @NotNull TipoOperacao tipoOperacao,
        @Schema(example = "10000.00", description = "Valor em BRL > 0") @NotNull @DecimalMin(value = "0.01")
                BigDecimal valorSolicitado,
        @Schema(example = "12", description = "Prazo em meses (>= 1)") @NotNull @Min(1) Integer prazoMeses) {}
