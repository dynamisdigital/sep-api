package com.dynamis.sep_api.backoffice.domain.event;

import com.dynamis.sep_api.backoffice.domain.model.Reprocesso;

import java.util.UUID;

/**
 * Publicado pelos use cases de reprocesso (Sprint 14 Task 14.4) apos persistir o registro
 * {@code Reprocesso}. Consumido por audit (Task 14.8).
 */
public record ReprocessoDisparadoEvent(
        UUID reprocessoId, Reprocesso.Tipo tipo, String identificadorExterno, UUID disparadoPor) {}
