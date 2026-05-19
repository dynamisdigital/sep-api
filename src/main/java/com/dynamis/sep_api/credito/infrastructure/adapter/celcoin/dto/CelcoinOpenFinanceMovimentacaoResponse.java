package com.dynamis.sep_api.credito.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Resposta Celcoin/Finansystech consolidando movimentacao bancaria do tomador (Sprint 9).
 *
 * <p>Mapeamento usa NUMERIC nos agregados; provider deve responder apenas com snapshot consolidado.
 * Caso o provider real retorne extrato bruto transacional, o {@code CelcoinOpenFinanceMapper}
 * agrega antes de produzir o {@code MovimentacaoConsolidada} do port (LGPD).
 */
public record CelcoinOpenFinanceMovimentacaoResponse(
        @JsonProperty("consent_id") String idConsentimento,
        @JsonProperty("media_entradas_mensal") BigDecimal mediaEntradasMensal,
        @JsonProperty("media_saidas_mensal") BigDecimal mediaSaidasMensal,
        @JsonProperty("saldo_medio") BigDecimal saldoMedio,
        @JsonProperty("meses_avaliados") Integer mesesAvaliados) {}
