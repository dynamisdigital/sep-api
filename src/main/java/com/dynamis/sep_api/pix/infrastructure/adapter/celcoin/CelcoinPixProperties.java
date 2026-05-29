package com.dynamis.sep_api.pix.infrastructure.adapter.celcoin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do {@code CelcoinPixProvider} (credenciais OAuth + base URL Celcoin Pix). Lidas de
 * {@code app.celcoin.pix.*} em {@code application.yml}.
 *
 * <p>O secret HMAC do webhook Pix NAO mora aqui — vive em {@code app.webhooks.secrets.celcoin-pix}
 * e e lido pelo {@code HmacSignatureValidator} (shared, Sprint 4).
 */
@ConfigurationProperties(prefix = "app.celcoin.pix")
public record CelcoinPixProperties(String baseUrl, String clientId, String clientSecret) {}
