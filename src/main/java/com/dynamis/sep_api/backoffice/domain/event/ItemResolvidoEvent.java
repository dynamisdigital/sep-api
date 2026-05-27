package com.dynamis.sep_api.backoffice.domain.event;

import java.util.UUID;

/**
 * Publicado pelo {@code MarcarItemResolvidoUseCase} (Sprint 14 Task 14.3). Consumido por audit
 * (Task 14.8).
 */
public record ItemResolvidoEvent(UUID itemId, UUID resolvidoPor, String justificativaResumida) {}
