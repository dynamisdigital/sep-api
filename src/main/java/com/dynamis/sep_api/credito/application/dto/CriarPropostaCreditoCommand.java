package com.dynamis.sep_api.credito.application.dto;

import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command de entrada do {@code CriarPropostaCreditoUseCase} (Sprint 8 Task 8.3). Validacao
 * declarativa de borda fica no DTO web; aqui assumimos dados ja validados.
 */
public record CriarPropostaCreditoCommand(
        UUID tomadorId,
        UUID solicitacaoOnboardingId,
        TipoOperacao tipoOperacao,
        BigDecimal valorSolicitado,
        Integer prazoMeses) {}
