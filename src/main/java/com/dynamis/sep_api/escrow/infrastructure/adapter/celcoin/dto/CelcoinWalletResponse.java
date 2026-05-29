package com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** Resposta da Celcoin para criacao/consulta de wallet e consulta de saldo. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CelcoinWalletResponse(
        @JsonProperty("wallet_id") String walletId, @JsonProperty("balance") BigDecimal saldo) {}
