package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload retornado pelo Celcoin ao consultar o resultado de uma verificacao previamente
 * disparada.
 *
 * @param status valores esperados: {@code APPROVED}, {@code REJECTED}, {@code PENDING},
 *     {@code PROCESSING}.
 * @param reason descricao curta quando rejected/pending; opcional.
 */
public record CelcoinKycResultadoResponse(
        @JsonProperty("verification_id") String idVerificacao,
        @JsonProperty("status") String status,
        @JsonProperty("reason") String reason) {}
