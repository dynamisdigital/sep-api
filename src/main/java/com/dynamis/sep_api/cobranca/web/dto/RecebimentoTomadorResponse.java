package com.dynamis.sep_api.cobranca.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Recebimento de uma parcela visivel ao tomador (Sprint 23). Projecao publica minima: sem escrow,
 * operador, idempotencia, identificador externo, observacao ou status tecnico.
 */
@Schema(description = "Recebimento de uma parcela do proprio tomador — apenas ID, valor, data e meio de pagamento.")
public record RecebimentoTomadorResponse(
        @Schema(description = "Identificador do recebimento", example = "018f7c2a-0e3d-7a91-9c4b-2f1e6d5a4b3c")
                UUID recebimentoId,
        @Schema(description = "Valor recebido", example = "1000.00") BigDecimal valorRecebido,
        @Schema(description = "Data e hora do recebimento", example = "2026-06-01T10:00:00-03:00")
                OffsetDateTime dataRecebimento,
        @Schema(description = "Meio de pagamento", example = "PIX") String meioPagamento) {}
