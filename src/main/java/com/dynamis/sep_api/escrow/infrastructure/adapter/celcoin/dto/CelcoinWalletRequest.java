package com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Corpo da requisicao de criacao de wallet sob conta escrow na Celcoin. Formato externo. */
public record CelcoinWalletRequest(
        @JsonProperty("account_id") String contaEscrowExternalId,
        @JsonProperty("reference_id") String referenceId,
        @JsonProperty("wallet_type") String tipoWallet) {}
