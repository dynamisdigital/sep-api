package com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Resposta da Celcoin para criacao/consulta de conta escrow. {@code status} cru mapeado no adapter. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CelcoinContaEscrowResponse(
        @JsonProperty("account_id") String accountId, @JsonProperty("status") String status) {}
