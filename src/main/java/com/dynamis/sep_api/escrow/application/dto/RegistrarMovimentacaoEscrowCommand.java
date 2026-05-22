package com.dynamis.sep_api.escrow.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando de entrada do {@code RegistrarMovimentacaoEscrowUseCase} (Sprint 12 Task 12.4).
 *
 * <p>{@code idempotencyKey} unica garante que a mesma chamada nao gera duas movimentacoes nem
 * dois creditos no saldo da wallet — defesa por {@code UNIQUE} no banco + check programatico.
 *
 * <p>{@code externalReferenceId} carrega o id da entidade externa que originou a movimentacao
 * (Sprint 12: {@code recebimento.id}).
 */
public record RegistrarMovimentacaoEscrowCommand(
        UUID propostaId,
        BigDecimal valor,
        String idempotencyKey,
        OffsetDateTime dataMovimentacao,
        UUID externalReferenceId) {

    public RegistrarMovimentacaoEscrowCommand {
        Objects.requireNonNull(propostaId, "propostaId obrigatorio");
        Objects.requireNonNull(valor, "valor obrigatorio");
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("valor deve ser positivo");
        }
        Objects.requireNonNull(idempotencyKey, "idempotencyKey obrigatoria");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey nao pode ser vazia");
        }
        Objects.requireNonNull(dataMovimentacao, "dataMovimentacao obrigatoria");
        Objects.requireNonNull(externalReferenceId, "externalReferenceId obrigatorio");
    }
}
