package com.dynamis.sep_api.credito.infrastructure.adapter.celcoin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do {@code CelcoinOpenFinanceProvider} (credenciais OAuth + base URL Finansystech).
 * Lidas de {@code app.celcoin.open-finance.*} em {@code application.yml}.
 *
 * <p>O secret HMAC do webhook NAO mora aqui — vive em {@code app.webhooks.secrets.celcoin-open-finance}
 * e e lido pelo {@code HmacSignatureValidator} (Sprint 4).
 */
@ConfigurationProperties(prefix = "app.celcoin.open-finance")
public record CelcoinOpenFinanceProperties(String baseUrl, String clientId, String clientSecret) {}
