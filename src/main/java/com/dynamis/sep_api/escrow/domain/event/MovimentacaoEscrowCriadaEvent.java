package com.dynamis.sep_api.escrow.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Disparado quando uma nova {@code MovimentacaoEscrow} eh persistida (Sprint 12 Task 12.7 audit).
 * Consumido por {@code CobrancaAuditListener} pra gravar {@code MOVIMENTACAO_ESCROW_CRIADA}.
 * Idempotente: reapresentacao da mesma {@code idempotencyKey} retorna a movimentacao existente
 * sem republicar o evento.
 */
public record MovimentacaoEscrowCriadaEvent(
        UUID movimentacaoId,
        UUID walletId,
        UUID propostaId,
        BigDecimal valor,
        String tipo,
        OffsetDateTime dataMovimentacao,
        UUID externalReferenceId) {}
