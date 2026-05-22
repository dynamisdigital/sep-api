package com.dynamis.sep_api.cobranca.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Disparado quando um {@code Recebimento} e persistido pela primeira vez (Sprint 12 Task 12.4).
 * Reapresentacao idempotente da mesma {@code Idempotency-Key} nao republica o evento.
 */
public record RecebimentoRegistradoEvent(
        UUID recebimentoId,
        UUID parcelaId,
        UUID agendaId,
        UUID contratoId,
        BigDecimal valorRecebido,
        OffsetDateTime dataRecebimento,
        String meioPagamento,
        UUID registradoPor) {}
