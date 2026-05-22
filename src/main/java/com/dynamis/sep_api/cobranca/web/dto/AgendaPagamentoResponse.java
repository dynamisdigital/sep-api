package com.dynamis.sep_api.cobranca.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Agenda de pagamento gerada apos contrato assinado (Sprint 12).")
public record AgendaPagamentoResponse(
        @Schema(example = "0f8d2b1a-aaaa-6f15-be21-5f51a516a416") UUID id,
        @Schema(example = "1f155daf-c0e8-6f15-be21-5f51a516a416") UUID contratoId,
        @Schema(example = "12") int numeroParcelas,
        @Schema(example = "12000.00") BigDecimal valorTotal,
        @Schema(example = "2026-05-22T10:00:00-03:00") OffsetDateTime dataGeracao,
        List<ParcelaResponse> parcelas) {}
