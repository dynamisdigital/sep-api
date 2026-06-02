package com.dynamis.sep_api.pix.application.port.out.dto;

import java.util.UUID;

/**
 * Resultado da baixa de parcela via {@code CobrancaRecebimentoPixPort} (Sprint 21 Task 21.4),
 * projetado para o {@code pix} sem expor {@code StatusParcela} de cobranca.
 *
 * @param recebimentoCobrancaId id do {@code Recebimento} criado em cobranca (vinculo de conciliacao).
 * @param parcelaQuitada parcela ficou {@code PAGA} apos a baixa (false = pagamento parcial).
 * @param novo {@code false} quando foi reapresentacao idempotente da mesma {@code idempotencyKey}.
 */
public record RecebimentoPixCobrancaResult(UUID recebimentoCobrancaId, boolean parcelaQuitada, boolean novo) {}
