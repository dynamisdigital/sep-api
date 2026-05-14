package com.dynamis.sep_api.onboarding.domain.event;

import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;

import java.util.UUID;

/**
 * Evento publicado ao concluir o ciclo PLD para uma solicitacao. {@code statusFinal} sera
 * {@code APROVADO_FINAL} (limpo em todas as bases pra todos os alvos) ou {@code REPROVADO_PLD}
 * (hit em ao menos uma base / alvo).
 */
public record PldFinalizadoEvent(UUID solicitacaoId, UUID usuarioId, StatusOnboarding statusFinal) {}
