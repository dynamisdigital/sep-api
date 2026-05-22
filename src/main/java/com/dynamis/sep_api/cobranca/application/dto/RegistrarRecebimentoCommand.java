package com.dynamis.sep_api.cobranca.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando do {@code RegistrarRecebimentoUseCase} (Sprint 12 Task 12.4). {@code idempotencyKey}
 * vem do header HTTP {@code Idempotency-Key} validado na borda REST (Task 12.6).
 */
public record RegistrarRecebimentoCommand(
        UUID parcelaId,
        BigDecimal valorRecebido,
        OffsetDateTime dataRecebimento,
        String meioPagamento,
        String identificadorExterno,
        String idempotencyKey,
        String observacao,
        UUID registradoPor) {

    public RegistrarRecebimentoCommand {
        Objects.requireNonNull(parcelaId, "parcelaId obrigatorio");
        Objects.requireNonNull(valorRecebido, "valorRecebido obrigatorio");
        if (valorRecebido.signum() <= 0) {
            throw new IllegalArgumentException("valorRecebido deve ser positivo");
        }
        Objects.requireNonNull(dataRecebimento, "dataRecebimento obrigatoria");
        Objects.requireNonNull(meioPagamento, "meioPagamento obrigatorio");
        if (meioPagamento.isBlank()) {
            throw new IllegalArgumentException("meioPagamento nao pode ser vazio");
        }
        Objects.requireNonNull(idempotencyKey, "idempotencyKey obrigatoria");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey nao pode ser vazia");
        }
        Objects.requireNonNull(registradoPor, "registradoPor obrigatorio");
    }
}
