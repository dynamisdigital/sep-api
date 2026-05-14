package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuracao do {@code CelcoinBackgroundCheckProvider}. Lidas de
 * {@code app.celcoin.background-check.*}.
 *
 * <p>OAuth centralizado em {@link CelcoinOAuthTokenProvider}; credenciais podem cair em
 * {@code app.celcoin.kyc.*} ou {@code app.celcoin.kyb.*} (ver resolver de credenciais).
 */
@ConfigurationProperties(prefix = "app.celcoin.background-check")
public record CelcoinBackgroundCheckProperties(String baseUrl, String clientId, String clientSecret) {}
