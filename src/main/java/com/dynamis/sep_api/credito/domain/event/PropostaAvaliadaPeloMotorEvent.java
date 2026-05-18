package com.dynamis.sep_api.credito.domain.event;

import com.dynamis.sep_api.credito.domain.vo.StatusProposta;

import java.util.UUID;

/**
 * Evento publicado apos o motor de regras avaliar a proposta e persistir score + regras avaliadas
 * (Sprint 8 Task 8.3). Consumido pelo listener de auditoria (Task 8.6).
 */
public record PropostaAvaliadaPeloMotorEvent(
        UUID propostaId, UUID tomadorId, int score, StatusProposta statusSugerido, int falhas, int pendencias) {}
