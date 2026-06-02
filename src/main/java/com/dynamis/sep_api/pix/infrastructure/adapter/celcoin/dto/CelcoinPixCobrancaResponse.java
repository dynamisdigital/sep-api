package com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta da Celcoin para a criacao de cobranca Pix de recebimento (Sprint 21 Task 21.2). Ecoa o
 * {@code txid} controlado pelo SEP, devolve o id de cobranca do provider e o copia-cola (EMV/QR).
 * Campos sao suposicao validada por WireMock (contrato Celcoin real e follow-up).
 */
public record CelcoinPixCobrancaResponse(
        @JsonProperty("txid") String txid,
        @JsonProperty("charge_id") String chargeId,
        @JsonProperty("emv") String copiaCola) {}
