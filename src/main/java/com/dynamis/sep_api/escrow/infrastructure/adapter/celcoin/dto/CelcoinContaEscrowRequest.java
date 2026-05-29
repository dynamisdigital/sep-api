package com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Corpo da requisicao de criacao de conta escrow na Celcoin. Formato externo. */
public record CelcoinContaEscrowRequest(@JsonProperty("holder") String titular) {}
