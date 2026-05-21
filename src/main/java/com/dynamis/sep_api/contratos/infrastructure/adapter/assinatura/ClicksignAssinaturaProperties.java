package com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do adapter Clicksign (Sprint 11 Task 11.4 / ADR 0013).
 *
 * <p>{@code accessToken} e secret de API (NAO commitar). {@code webhookHmacSecret} valida
 * assinatura HMAC dos callbacks recebidos no {@code AssinaturaWebhookController} (Task 11.6).
 */
@ConfigurationProperties(prefix = "app.assinatura.clicksign")
public record ClicksignAssinaturaProperties(String baseUrl, String accessToken, Webhook webhook) {

    public record Webhook(String hmacSecret) {}
}
