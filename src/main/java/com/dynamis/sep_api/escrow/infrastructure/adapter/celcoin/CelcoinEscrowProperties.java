package com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do {@code CelcoinEscrowProvider} (credenciais OAuth + base URL Celcoin escrow).
 * Lidas de {@code app.celcoin.escrow.*} em {@code application.yml}.
 */
@ConfigurationProperties(prefix = "app.celcoin.escrow")
public record CelcoinEscrowProperties(String baseUrl, String clientId, String clientSecret) {}
