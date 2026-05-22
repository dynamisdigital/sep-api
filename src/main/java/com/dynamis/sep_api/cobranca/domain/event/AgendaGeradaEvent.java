package com.dynamis.sep_api.cobranca.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Disparado quando uma nova {@code AgendaPagamento} e persistida (Sprint 12 Task 12.3). Nao
 * republicar para agenda idempotente ja existente.
 */
public record AgendaGeradaEvent(
        UUID agendaId,
        UUID contratoId,
        UUID propostaId,
        UUID tomadorId,
        int numeroParcelas,
        BigDecimal valorTotal,
        OffsetDateTime dataGeracao) {}
