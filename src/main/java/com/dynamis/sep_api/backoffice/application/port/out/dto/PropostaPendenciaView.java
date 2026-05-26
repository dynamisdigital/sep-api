package com.dynamis.sep_api.backoffice.application.port.out.dto;

import java.util.UUID;

/**
 * Projecao minima de proposta parada (Sprint 14 Task 14.2 — fix review manual). O modulo
 * {@code backoffice} consome apenas o id pra criar item da fila; quaisquer detalhes adicionais
 * vem via {@code GET /api/v1/credito/...} quando o operador abrir o item.
 */
public record PropostaPendenciaView(UUID propostaId) {}
