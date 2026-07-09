package com.dynamis.sep_api.credores.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando do registro assistido de aporte (Sprint 29 Task 29.3). {@code atorId} e o usuario
 * financeiro/admin autenticado; {@code idempotencyKey} vem do header {@code Idempotency-Key}.
 * Validacao de negocio (chave, valor) ocorre no use case com erros 400.
 */
public record RegistrarAporteCredoraCommand(UUID operacaoId, BigDecimal valor, String idempotencyKey, UUID atorId) {}
