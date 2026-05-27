package com.dynamis.sep_api.backoffice.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Publicado pelo {@code AssumirItemFilaUseCase} (Sprint 14 Task 14.3). Consumido por audit (Task
 * 14.8).
 */
public record ItemAssumidoEvent(UUID itemId, UUID atribuidoA, OffsetDateTime atribuidoEm) {}
