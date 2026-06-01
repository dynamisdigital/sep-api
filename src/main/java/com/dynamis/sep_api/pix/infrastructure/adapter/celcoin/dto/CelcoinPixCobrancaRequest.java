package com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Corpo da requisicao de criacao de cobranca Pix de recebimento para a Celcoin (Sprint 21 Task
 * 21.2). Formato externo — nao trafega no dominio. O {@code txid} eh controlado pelo SEP. Campos
 * sao suposicao validada por WireMock (contrato Celcoin real e follow-up, ver {@code PIX.md}).
 */
public record CelcoinPixCobrancaRequest(
        @JsonProperty("txid") String txid, @JsonProperty("amount") BigDecimal valor) {}
