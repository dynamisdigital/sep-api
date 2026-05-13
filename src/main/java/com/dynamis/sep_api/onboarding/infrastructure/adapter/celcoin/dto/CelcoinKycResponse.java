package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Payload retornado pelo endpoint Celcoin Onboarding KYC PF (resposta sincrona ao disparo). */
public record CelcoinKycResponse(
        @JsonProperty("verification_id") String idVerificacao, @JsonProperty("status") String status) {}
