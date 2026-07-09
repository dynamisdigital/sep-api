package com.dynamis.sep_api.credores.domain.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aporte assistido registrado para operacao financiada da credora (Sprint 29 Task 29.3). Publicado
 * uma unica vez por aporte novo — replay idempotente nao republica. {@code usuarioId} e o ator
 * financeiro/admin que registrou.
 */
public record AporteCredoraRegistradoEvent(
        UUID aporteId, UUID operacaoId, UUID empresaCredoraId, BigDecimal valor, UUID usuarioId) {}
