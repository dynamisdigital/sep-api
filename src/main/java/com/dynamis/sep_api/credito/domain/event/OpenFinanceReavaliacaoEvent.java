package com.dynamis.sep_api.credito.domain.event;

import com.dynamis.sep_api.credito.domain.vo.StatusProposta;

import java.util.UUID;

/**
 * Reavaliacao da proposta pos Open Finance (Sprint 9 Task 9.4). Carrega comparativo score
 * antes/depois e status atual/novo pra trilha auditavel (Task 9.7).
 */
public record OpenFinanceReavaliacaoEvent(
        UUID propostaId,
        UUID tomadorId,
        UUID consentimentoId,
        int scoreAnterior,
        int scoreNovo,
        StatusProposta statusAnterior,
        StatusProposta statusNovo) {}
