package com.dynamis.sep_api.shared.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Envelope canonico de webhook recebido")
public record WebhookEnvelopeDto(
        @Schema(example = "celcoin") String provider,
        @Schema(example = "pagamento_recebido") String event,
        @Schema(example = "evt_01JABC") String idempotencyKey,
        @Schema(example = "{\"id\":\"...\"}") String payload) {}
