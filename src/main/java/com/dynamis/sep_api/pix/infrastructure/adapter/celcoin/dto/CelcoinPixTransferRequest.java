package com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Corpo da requisicao de transferencia Pix para a Celcoin. Formato externo — nao trafega no
 * dominio. {@code chaveDestino} so existe aqui (input do provider), nunca persistido.
 */
public record CelcoinPixTransferRequest(
        @JsonProperty("amount") BigDecimal valor,
        @JsonProperty("pix_key") String chaveDestino,
        @JsonProperty("description") String descricao) {}
