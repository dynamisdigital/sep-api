package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuracao do {@code CelcoinKybProvider}. Lidas de
 * {@code app.celcoin.kyb.*}.
 *
 * <p>OAuth e centralizado em {@link CelcoinOAuthTokenProvider} (compartilhado com KYC); as
 * credenciais do KYB caem nas mesmas variaveis ({@code APP_CELCOIN_CLIENT_ID}/{@code _SECRET}).
 * Base URL e separada porque o produto pode evoluir pra endpoints distintos.
 *
 * <p>Secret HMAC do webhook KYB NAO mora aqui — vive em
 * {@code app.webhooks.secrets.celcoin-kyb} e e lido diretamente pelo validador (Sprint 4).
 */
@ConfigurationProperties(prefix = "app.celcoin.kyb")
public record CelcoinKybProperties(String baseUrl, String clientId, String clientSecret) {}
