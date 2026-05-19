package com.dynamis.sep_api.credito.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Snapshot consolidado de movimentacao bancaria — apenas agregados (LGPD). Sprint 9 Task 9.6. */
@Schema(description = "Snapshot consolidado de movimentacao Open Finance")
public record MovimentacaoConsolidadaResponse(
        BigDecimal mediaEntradasMensal,
        BigDecimal mediaSaidasMensal,
        BigDecimal saldoMedio,
        Integer numeroMesesAvaliados,
        OffsetDateTime dataRecebimento) {}
