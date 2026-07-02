package com.dynamis.sep_api.cobranca.web.dto;

import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Termos de uma renegociacao ativa visiveis ao proprio tomador antes da decisao (Sprint 24).
 * Projecao publica minima: sem {@code tomadorId}, {@code propostaPor}, IDs de agenda,
 * {@code statusParcelaAnterior} ou justificativa operacional.
 */
@Schema(
        description = "Termos financeiros de uma renegociacao ativa da propria parcela — o suficiente para o"
                + " tomador aceitar ou recusar, sem dados operacionais.")
public record RenegociacaoTomadorResponse(
        @Schema(description = "Identificador da renegociacao", example = "018f7c2a-0e3d-7a91-9c4b-2f1e6d5a4b3c")
                UUID renegociacaoId,
        @Schema(description = "Identificador da parcela original", example = "018f7c2a-0e3d-7a91-9c4b-2f1e6d5a4b3d")
                UUID parcelaId,
        @Schema(description = "Status da proposta (sempre PROPOSTA quando retornada)") StatusRenegociacao status,
        @Schema(description = "Novo valor de cada parcela", example = "200.00") BigDecimal novoValorParcela,
        @Schema(description = "Numero de parcelas substitutas", example = "3") int numeroParcelas,
        @Schema(description = "Valor total renegociado, calculado no backend", example = "600.00")
                BigDecimal valorTotalRenegociado,
        @Schema(description = "Vencimento da primeira parcela substituta", example = "2026-07-01")
                LocalDate novoVencimento,
        @Schema(description = "Desconto aplicado na renegociacao", example = "50.00") BigDecimal desconto,
        @Schema(description = "Quando a proposta foi criada", example = "2026-06-14T10:00:00-03:00")
                OffsetDateTime dataProposta,
        @Schema(description = "Quando a proposta expira", example = "2026-06-21T10:00:00-03:00")
                OffsetDateTime dataExpiracao) {}
