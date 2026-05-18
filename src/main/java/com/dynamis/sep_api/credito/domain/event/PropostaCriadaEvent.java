package com.dynamis.sep_api.credito.domain.event;

import java.util.UUID;

/**
 * Evento publicado quando uma {@code PropostaCredito} e criada com sucesso. Disparado pelo
 * {@code CriarPropostaCreditoUseCase} (Sprint 8 Task 8.3) e consumido por listener interno que
 * agenda a avaliacao automatica via motor de regras.
 */
public record PropostaCriadaEvent(UUID propostaId, UUID tomadorId, UUID solicitacaoOnboardingId) {}
