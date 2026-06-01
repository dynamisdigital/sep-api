package com.dynamis.sep_api.pix.domain.event;

import java.util.UUID;

/**
 * Transferencia de desembolso falhou (rejeicao do provider ou falha tecnica). Sprint 20 Task 20.3 —
 * consumido pelo backoffice/auditoria na Task 20.4.
 */
public record PixTransferenciaFalhouEvent(UUID transferenciaId, UUID contratoId, String motivo) {}
