package com.dynamis.sep_api.credores.domain.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aporte da credora liquidado na reconciliacao (Sprint 29 Task 29.5). Publicado somente na primeira
 * liquidacao — replay idempotente nao republica. Sem ator humano: reconciliacao e acionada pelo
 * provider fake/sistema.
 */
public record AporteCredoraLiquidadoEvent(UUID aporteId, UUID operacaoId, UUID empresaCredoraId, BigDecimal valor) {}
