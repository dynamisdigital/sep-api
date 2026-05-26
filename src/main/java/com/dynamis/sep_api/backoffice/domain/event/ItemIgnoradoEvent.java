package com.dynamis.sep_api.backoffice.domain.event;

import java.util.UUID;

/**
 * Publicado pelo {@code MarcarItemIgnoradoUseCase} (Sprint 14 Task 14.3). Consumido por audit
 * (Task 14.8).
 */
public record ItemIgnoradoEvent(UUID itemId, UUID ignoradoPor, String justificativaResumida) {}
