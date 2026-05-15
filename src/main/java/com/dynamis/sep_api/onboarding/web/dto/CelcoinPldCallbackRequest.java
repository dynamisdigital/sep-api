package com.dynamis.sep_api.onboarding.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * Payload do webhook Celcoin Background Check (PLD). Consolidado: traz todos os alvos
 * (PESSOA/EMPRESA/REPRESENTANTE) ja consultados nas 4 bases. {@code external_id} = solicitacaoId.
 */
public record CelcoinPldCallbackRequest(
        @JsonProperty("external_id") String externalId, @JsonProperty("results") List<AlvoResultado> alvos) {

    public record AlvoResultado(
            @JsonProperty("target_type") String alvoTipo,
            @JsonProperty("document_number") String documento,
            @JsonProperty("databases") List<BaseResultado> bases) {}

    public record BaseResultado(
            @JsonProperty("database") String base,
            @JsonProperty("hit") boolean hit,
            @JsonProperty("reason") String motivo,
            @JsonProperty("severity") String severidade,
            @JsonProperty("inclusion_date") LocalDate dataInclusao) {}
}
