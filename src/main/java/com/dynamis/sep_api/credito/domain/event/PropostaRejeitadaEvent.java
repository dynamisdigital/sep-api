package com.dynamis.sep_api.credito.domain.event;

import com.dynamis.sep_api.credito.domain.vo.OrigemDecisao;

import java.util.UUID;

/**
 * Evento de dominio: proposta rejeitada definitivamente, seja pelo motor (rejeicao automatica por
 * bloqueio absoluto) ou por parecer manual {@code REJEITAR} (Sprint 8). {@code origem} distingue
 * os dois fluxos pra auditoria.
 */
public record PropostaRejeitadaEvent(UUID propostaId, UUID tomadorId, OrigemDecisao origem, UUID parecerId) {}
