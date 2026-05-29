package com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Envelope cru do webhook Pix da Celcoin. Parseado pelo {@code PixWebhookNormalizer}; campos
 * variam conforme {@code event_type}. {@link JsonIgnoreProperties} tolera campos extras do provider.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CelcoinPixWebhookPayload(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("end_to_end_id") String endToEndId,
        @JsonProperty("amount") BigDecimal valor,
        @JsonProperty("transfer_id") String transferId) {}
