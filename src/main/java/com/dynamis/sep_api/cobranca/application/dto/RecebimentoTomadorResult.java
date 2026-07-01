package com.dynamis.sep_api.cobranca.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Projecao publica minima de um {@link com.dynamis.sep_api.cobranca.domain.model.Recebimento} para o
 * tomador (Sprint 23). Expoe apenas ID, valor, data e meio de pagamento — nunca escrow, operador,
 * idempotencia, identificador externo, observacao ou status tecnico.
 */
public record RecebimentoTomadorResult(
        UUID recebimentoId, BigDecimal valorRecebido, OffsetDateTime dataRecebimento, String meioPagamento) {}
