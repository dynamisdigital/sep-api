package com.dynamis.sep_api.cobranca.application.port.out;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Porta de saida do modulo {@code cobranca} pra registrar entrada de recurso no escrow (Sprint 12
 * Task 12.4). Adapter concreto em {@code cobranca.infrastructure.adapter.escrow} traduz pra
 * {@code escrow.application.usecase.RegistrarMovimentacaoEscrowUseCase}. Fronteira hexagonal: o
 * use case de cobranca nao conhece a persistencia ou entidades do escrow.
 */
public interface RegistrarMovimentacaoEscrowPort {

    /**
     * Idempotente por {@code idempotencyKey}: chamadas repetidas com a mesma chave retornam o
     * resultado existente sem novo credito no saldo do escrow.
     */
    MovimentacaoEscrowResult registrarRecebimento(
            UUID propostaId,
            BigDecimal valor,
            String idempotencyKey,
            OffsetDateTime dataMovimentacao,
            UUID externalReferenceId);

    /** View read-only retornada pela porta — UUID + valor confirmado. */
    record MovimentacaoEscrowResult(UUID movimentacaoId, UUID walletId, BigDecimal valor) {}
}
