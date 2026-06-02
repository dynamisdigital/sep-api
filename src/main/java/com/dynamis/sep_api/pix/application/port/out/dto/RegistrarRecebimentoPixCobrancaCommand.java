package com.dynamis.sep_api.pix.application.port.out.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Comando da porta {@code CobrancaRecebimentoPixPort} (Sprint 21 Task 21.4): pede a {@code cobranca}
 * a baixa de uma parcela a partir de um recebimento Pix conciliado. Linguagem do {@code pix} — o
 * adapter traduz para {@code RegistrarRecebimentoCommand} de cobranca.
 *
 * @param idempotencyKey chave deterministica {@code pix:<endToEndId>} — garante baixa e movimentacao
 *     escrow uma unica vez mesmo em replay.
 * @param identificadorExterno {@code endToEndId} do Pix (rastreabilidade do recebimento de cobranca).
 * @param registradoPor ator atribuido a baixa; para recebimento Pix automatico, o {@code tomadorId}
 *     da parcela (o pagador) — nao ha operador manual.
 */
public record RegistrarRecebimentoPixCobrancaCommand(
        UUID parcelaId,
        BigDecimal valorRecebido,
        OffsetDateTime dataRecebimento,
        String idempotencyKey,
        String identificadorExterno,
        UUID registradoPor) {}
