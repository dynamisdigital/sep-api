package com.dynamis.sep_api.credito.domain.event;

import java.util.UUID;

/**
 * Evento publicado apos snapshot de movimentacao Open Finance ser persistido (Sprint 9). Consumido
 * pelo audit listener (Task 9.7) e pelo listener de reavaliacao (Task 9.4).
 */
public record OpenFinanceDadosRecebidosEvent(
        UUID movimentacaoId, UUID consentimentoId, UUID propostaId, UUID tomadorId, Integer numeroMesesAvaliados) {}
