package com.dynamis.sep_api.pix.domain.event;

import java.math.BigDecimal;
import java.util.UUID;

/** Transferencia de desembolso aceita pelo provider (tem {@code externalId}). Sprint 20 Task 20.3. */
public record PixTransferenciaSolicitadaEvent(
        UUID transferenciaId, UUID contratoId, String externalId, BigDecimal valor) {}
