package com.dynamis.sep_api.contratos.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * Payload minimo de callback Clicksign (Sprint 11 Task 11.6). Campos desconhecidos sao ignorados
 * ({@link JsonIgnoreProperties}) — Clicksign envia muitos campos extras (signer, account etc) que
 * nao influenciam o ciclo do envelope no SEP.
 *
 * <p>Mapeamento {@code event.name -> StatusEnvelope} feito em
 * {@code ProcessarWebhookAssinaturaUseCase}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssinaturaCallbackClicksign(EventDto event, DocumentDto document) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventDto(@JsonProperty("name") String name, @JsonProperty("occurred_at") OffsetDateTime occurredAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocumentDto(@JsonProperty("key") String key) {}
}
