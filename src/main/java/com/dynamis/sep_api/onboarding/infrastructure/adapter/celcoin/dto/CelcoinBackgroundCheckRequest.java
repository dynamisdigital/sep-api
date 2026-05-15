package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Payload enviado ao endpoint Celcoin Background Check. Estrutura inferida da documentacao
 * Celcoin (sandbox); evoluir conforme contrato real estabilizar.
 */
public record CelcoinBackgroundCheckRequest(
        @JsonProperty("external_id") String externalId,
        @JsonProperty("target_type") String tipoAlvo,
        @JsonProperty("document_number") String documento,
        @JsonProperty("full_name") String nome,
        @JsonProperty("databases") List<String> bases) {}
