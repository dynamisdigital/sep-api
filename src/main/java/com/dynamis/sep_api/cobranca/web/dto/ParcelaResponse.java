package com.dynamis.sep_api.cobranca.web.dto;

import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Parcela individual de uma AgendaPagamento (Sprint 12).")
public record ParcelaResponse(
        @Schema(example = "1f155daf-c0e8-6f15-be21-5f51a516a416") UUID id,
        @Schema(example = "1") int numero,
        @Schema(example = "850.00") BigDecimal principal,
        @Schema(example = "150.00") BigDecimal juros,
        @Schema(example = "0.00") BigDecimal multa,
        @Schema(example = "0.00") BigDecimal encargos,
        @Schema(example = "1000.00") BigDecimal total,
        @Schema(example = "2026-06-15") LocalDate dataVencimento,
        @Schema(example = "PENDENTE") StatusParcela status) {}
