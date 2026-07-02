package com.dynamis.sep_api.cobranca.application.dto;

import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Projecao publica minima de uma {@link com.dynamis.sep_api.cobranca.domain.model.Renegociacao}
 * ativa para o tomador decidir (Sprint 24 — desbloqueio B2 da M-Sprint 9). Expoe apenas os termos
 * financeiros necessarios a decisao: nunca justificativa operacional, operador, IDs de agenda,
 * tomador ou status da parcela anterior.
 *
 * <p>{@code valorTotalRenegociado} e calculado no backend ({@code novoValorParcela * numeroParcelas})
 * — o mobile nunca deriva o total.
 */
public record RenegociacaoTomadorResult(
        UUID renegociacaoId,
        UUID parcelaId,
        StatusRenegociacao status,
        BigDecimal novoValorParcela,
        int numeroParcelas,
        BigDecimal valorTotalRenegociado,
        LocalDate novoVencimento,
        BigDecimal desconto,
        OffsetDateTime dataProposta,
        OffsetDateTime dataExpiracao) {}
