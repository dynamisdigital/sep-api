package com.dynamis.sep_api.cobranca.web.dto;

import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Snapshot do valor atualizado de uma parcela contra 'agora' (Sprint 12 Task 12.5).")
public record ValorAtualizadoParcelaResponse(
        UUID parcelaId,
        int numero,
        StatusParcela status,
        LocalDate dataVencimento,
        @Schema(example = "1000.00") BigDecimal principalOriginal,
        @Schema(example = "150.00") BigDecimal jurosOriginal,
        @Schema(example = "10.00") BigDecimal jurosMora,
        @Schema(example = "20.00") BigDecimal multa,
        @Schema(example = "1180.00") BigDecimal valorDevidoAtualizado,
        @Schema(example = "0.00") BigDecimal totalRecebido,
        @Schema(example = "1180.00") BigDecimal valorEmAberto) {}
