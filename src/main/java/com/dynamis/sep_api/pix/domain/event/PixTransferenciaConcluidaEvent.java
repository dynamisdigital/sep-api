package com.dynamis.sep_api.pix.domain.event;

import java.util.UUID;

/** Transferencia de desembolso liquidada pelo provider. Sprint 20 Task 20.3. */
public record PixTransferenciaConcluidaEvent(
        UUID transferenciaId, UUID contratoId, UUID tomadorId, String externalId) {}
