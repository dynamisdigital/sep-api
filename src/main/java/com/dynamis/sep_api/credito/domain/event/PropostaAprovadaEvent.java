package com.dynamis.sep_api.credito.domain.event;

import java.util.UUID;

/**
 * Evento de dominio: proposta aprovada definitivamente apos parecer manual {@code APROVAR} (Sprint
 * 8 Task 8.4). Sera consumido na Sprint 10 (Formalizacao) pra iniciar geracao de contrato.
 */
public record PropostaAprovadaEvent(UUID propostaId, UUID tomadorId, UUID parecerId) {}
