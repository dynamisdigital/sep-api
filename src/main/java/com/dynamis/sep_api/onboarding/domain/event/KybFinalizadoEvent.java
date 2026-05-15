package com.dynamis.sep_api.onboarding.domain.event;

import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;

import java.util.UUID;

/**
 * Evento publicado quando a verificacao KYB termina (pre-PLD). {@code statusFinal} sera
 * {@code APROVADO} (situacao ATIVA — habilita PLD) ou {@code REPROVADO} (demais situacoes).
 */
public record KybFinalizadoEvent(UUID solicitacaoId, UUID usuarioId, StatusOnboarding statusFinal, UUID kybEmpresaId) {}
