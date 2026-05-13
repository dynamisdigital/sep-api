package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuracao do {@code CelcoinKycProvider}. Lidas de {@code app.celcoin.kyc.*}
 * em {@code application.yml}.
 */
@ConfigurationProperties(prefix = "app.celcoin.kyc")
public record CelcoinKycProperties(String baseUrl, String clientId, String clientSecret, String webhookSecret) {}
