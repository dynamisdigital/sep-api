package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuracao do {@code CelcoinKycProvider} (credenciais OAuth + base URL). Lidas
 * de {@code app.celcoin.kyc.*} em {@code application.yml}.
 *
 * <p>O secret HMAC do webhook NAO mora aqui — vive em {@code app.webhooks.secrets.celcoin-kyc} e
 * e lido diretamente pelo {@code HmacSignatureValidator} (Sprint 4). Fonte unica evita risco
 * operacional de configurar so um dos dois e ter callbacks reais falhando com 401.
 */
@ConfigurationProperties(prefix = "app.celcoin.kyc")
public record CelcoinKycProperties(String baseUrl, String clientId, String clientSecret) {}
