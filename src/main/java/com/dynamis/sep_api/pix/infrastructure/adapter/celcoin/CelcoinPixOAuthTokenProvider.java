package com.dynamis.sep_api.pix.infrastructure.adapter.celcoin;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;

/**
 * OAuth2 client-credentials para Celcoin Pix (Epic 15 / Sprint 19). Cache em memoria com clock skew
 * de 30s + sync interna pra evitar refresh paralelo. Espelha o padrao de OAuth do modulo credito,
 * com bloco de credenciais dedicado ({@code app.celcoin.pix.*}) pra preservar isolamento do modulo.
 */
@Component
@ConditionalOnProperty(name = "app.pix.provider", havingValue = "celcoin")
public class CelcoinPixOAuthTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(CelcoinPixOAuthTokenProvider.class);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final CelcoinPixProperties properties;
    private final RestClient tokenClient;
    private final Object lock = new Object();

    private volatile CachedToken cache;

    public CelcoinPixOAuthTokenProvider(CelcoinPixProperties properties) {
        this.properties = properties;
        this.tokenClient = RestClient.builder().build();
    }

    public void resetCache() {
        cache = null;
    }

    public String accessToken() {
        if (isBlank(properties.clientId()) || isBlank(properties.clientSecret())) {
            throw new IllegalStateException(
                    "Credenciais Celcoin Pix ausentes: configure app.celcoin.pix.client-id e client-secret");
        }
        CachedToken atual = cache;
        if (atual != null && Instant.now().isBefore(atual.expiresAt().minus(CLOCK_SKEW))) {
            return atual.token();
        }
        synchronized (lock) {
            CachedToken depoisDoLock = cache;
            if (depoisDoLock != null
                    && Instant.now().isBefore(depoisDoLock.expiresAt().minus(CLOCK_SKEW))) {
                return depoisDoLock.token();
            }
            CachedToken fresh = renovar();
            cache = fresh;
            return fresh.token();
        }
    }

    private CachedToken renovar() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        try {
            TokenResponse response = tokenClient
                    .post()
                    .uri(tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || isBlank(response.accessToken())) {
                throw new IllegalStateException("Resposta OAuth Celcoin Pix sem access_token");
            }
            long expiresInSec = response.expiresIn() > 0 ? response.expiresIn() : 3600;
            log.info("Celcoin Pix OAuth token renovado expira em {}s", expiresInSec);
            return new CachedToken(response.accessToken(), Instant.now().plusSeconds(expiresInSec));
        } catch (RestClientResponseException ex) {
            log.warn("Falha ao renovar OAuth Celcoin Pix status={}", ex.getStatusCode().value());
            throw ex;
        }
    }

    private String tokenUrl() {
        String base = properties.baseUrl() == null ? "" : properties.baseUrl();
        return base.endsWith("/") ? base + "token" : base + "/token";
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    private record CachedToken(String token, Instant expiresAt) {}

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn) {}
}
