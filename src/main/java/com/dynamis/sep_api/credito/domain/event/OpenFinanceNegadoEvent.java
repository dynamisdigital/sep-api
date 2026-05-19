package com.dynamis.sep_api.credito.domain.event;

import java.util.UUID;

/**
 * Evento publicado quando consentimento Open Finance e negado pelo tomador (Sprint 9). Consumido
 * pelo audit listener (Task 9.7) pra gravar {@code OPEN_FINANCE_NEGADO}. NAO altera score.
 */
public record OpenFinanceNegadoEvent(UUID consentimentoId, UUID propostaId, UUID tomadorId) {}
