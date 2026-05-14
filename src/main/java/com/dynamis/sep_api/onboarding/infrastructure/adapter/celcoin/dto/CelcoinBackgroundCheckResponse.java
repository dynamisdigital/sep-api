package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * Payload retornado pelo endpoint Celcoin Background Check consolidado. Devolve uma lista de
 * resultados por base; hit por base e indicado pela flag {@code hit}.
 */
public record CelcoinBackgroundCheckResponse(@JsonProperty("results") List<ResultadoBase> resultados) {

    public record ResultadoBase(
            @JsonProperty("database") String base,
            @JsonProperty("hit") boolean hit,
            @JsonProperty("reason") String motivo,
            @JsonProperty("severity") String severidade,
            @JsonProperty("inclusion_date") LocalDate dataInclusao) {}
}
