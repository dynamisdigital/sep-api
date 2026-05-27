package com.dynamis.sep_api.credito.application.dto;

import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command de entrada do {@code CriarPropostaCreditoUseCase} (Sprint 8 Task 8.3). Validacao
 * declarativa de borda fica no DTO web; aqui assumimos dados ja validados.
 *
 * <p>Sprint 15 Task 15.5 (15F-018): {@code prazoMeses} agora e {@code int} primitivo — nao admite
 * null. DTO web ja exige {@code @NotNull @Min(1)}; converter para primitivo no command remove
 * fallback null->0 perigoso no use case.
 */
public record CriarPropostaCreditoCommand(
        UUID tomadorId,
        UUID solicitacaoOnboardingId,
        TipoOperacao tipoOperacao,
        BigDecimal valorSolicitado,
        int prazoMeses) {}
