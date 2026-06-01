package com.dynamis.sep_api.pix.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando de solicitacao de desembolso Pix assistido (Sprint 20 Task 20.2).
 *
 * @param chavePixDestino chave em claro — usada apenas para hash/mascara/provider, nunca persistida.
 * @param operadorId operador autenticado (FINANCEIRO/ADMIN) que dispara o desembolso, para trilha.
 */
public record SolicitarDesembolsoPixCommand(
        UUID contratoId,
        BigDecimal valor,
        String chavePixDestino,
        String idempotencyKey,
        UUID operadorId,
        String correlationId) {}
