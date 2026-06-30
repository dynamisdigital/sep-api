package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth2 client-credentials para Celcoin. Compartilhado entre adapters KYC, KYB e PLD.
 *
 * <p>O cache de token e <em>por credencial</em> ({@code clientId@baseUrl}) — duas configuracoes
 * Celcoin distintas convivem sem contaminar token entre elas. Cada caller passa um
 * {@link ProviderKey}; o resolver escolhe o bloco de credenciais ({@code app.celcoin.<X>.*}) com
 * prioridade no proprio bloco (KYC->KYC, KYB->KYB, PLD->Background Check) e so cai para outro
 * bloco se o proprio nao tiver credenciais preenchidas.
 *
 * <p>Endpoint default Celcoin: {@code POST {token-url}} com {@code grant_type=client_credentials}.
 * Em producao a URL real costuma ser separada (ex.: {@code https://api-onboarding.celcoin.dev/v5/token/oauth}).
 *
 * <p>Concorrencia: cache externo via {@link ConcurrentHashMap}; renovacao por chave de cache
 * serializada com sync interna pra evitar dois refreshes simultaneos da mesma credencial.
 */
@Component
@ConditionalOnExpression("'${app.kyc.provider:fake}'.equals('celcoin') "
        + "or '${app.kyb.provider:fake}'.equals('celcoin') "
        + "or '${app.pld.provider:fake}'.equals('celcoin')")
public class CelcoinOAuthTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(CelcoinOAuthTokenProvider.class);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    public enum ProviderKey {
        KYC,
        KYB,
        PLD
    }

    private final CelcoinKycProperties kycProperties;
    private final CelcoinKybProperties kybProperties;
    private final CelcoinBackgroundCheckProperties backgroundCheckProperties;
    private final RestClient tokenClient;

    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    public CelcoinOAuthTokenProvider(
            CelcoinKycProperties kycProperties,
            CelcoinKybProperties kybProperties,
            CelcoinBackgroundCheckProperties backgroundCheckProperties) {
        this.kycProperties = kycProperties;
        this.kybProperties = kybProperties;
        this.backgroundCheckProperties = backgroundCheckProperties;
        this.tokenClient = RestClient.builder().build();
    }

    /**
     * Devolve um access token valido para o adapter chamador. Cache e por {@code clientId@baseUrl},
     * evitando que adapters com credenciais distintas compartilhem token incorretamente.
     */
    /**
     * Limpa o cache de tokens — uso operacional (forcar renovacao em runbook) e de teste. Em
     * producao normal nao deve ser chamado.
     */
    public void resetCache() {
        cache.clear();
    }

    public String accessToken(ProviderKey providerKey) {
        Creds creds = resolverCreds(providerKey);
        if (creds == null) {
            throw new IllegalStateException("Credenciais Celcoin ausentes para " + providerKey + ": configure "
                    + "app.celcoin.kyc.*, app.celcoin.kyb.* ou app.celcoin.background-check.* "
                    + "(client-id e client-secret)");
        }
        String cacheKey = creds.cacheKey();
        CachedToken atual = cache.get(cacheKey);
        if (atual != null && Instant.now().isBefore(atual.expiresAt().minus(CLOCK_SKEW))) {
            return atual.token();
        }
        synchronized (cacheKey.intern()) {
            CachedToken depoisDoLock = cache.get(cacheKey);
            if (depoisDoLock != null
                    && Instant.now().isBefore(depoisDoLock.expiresAt().minus(CLOCK_SKEW))) {
                return depoisDoLock.token();
            }
            CachedToken fresh = renovar(creds);
            cache.put(cacheKey, fresh);
            return fresh.token();
        }
    }

    private CachedToken renovar(Creds creds) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", creds.clientId());
        form.add("client_secret", creds.clientSecret());

        try {
            TokenResponse response = tokenClient
                    .post()
                    .uri(tokenUrl(creds))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || isBlank(response.accessToken())) {
                throw new IllegalStateException("Resposta OAuth Celcoin sem access_token");
            }
            long expiresInSec = response.expiresIn() > 0 ? response.expiresIn() : 3600;
            log.info("Celcoin OAuth token renovado; expira em {}s", expiresInSec);
            return new CachedToken(response.accessToken(), Instant.now().plusSeconds(expiresInSec));
        } catch (RestClientResponseException ex) {
            // Nao logar body — pode conter detalhes/secret.
            log.warn(
                    "Falha ao renovar OAuth Celcoin status={} cache={}",
                    ex.getStatusCode().value(),
                    creds.cacheKey());
            throw ex;
        }
    }

    private static String tokenUrl(Creds creds) {
        String base = creds.baseUrl() == null ? "" : creds.baseUrl();
        return base.endsWith("/") ? base + "token" : base + "/token";
    }

    /**
     * Resolve credenciais OAuth + baseUrl como uma unica unidade. Cada chamador prioriza o proprio
     * bloco de configuracao; cai para outros blocos apenas se o proprio nao tiver credenciais.
     * Retorna {@code null} quando nenhum bloco tem credenciais — caller lanca IllegalState.
     */
    private Creds resolverCreds(ProviderKey providerKey) {
        return switch (providerKey) {
            case KYC -> firstNonNull(
                    credsFrom(kycProperties), credsFrom(kybProperties), credsFrom(backgroundCheckProperties));
            case KYB -> firstNonNull(
                    credsFrom(kybProperties), credsFrom(kycProperties), credsFrom(backgroundCheckProperties));
            case PLD -> firstNonNull(
                    credsFrom(backgroundCheckProperties), credsFrom(kycProperties), credsFrom(kybProperties));
        };
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... candidates) {
        for (T c : candidates) {
            if (c != null) return c;
        }
        return null;
    }

    private static Creds credsFrom(CelcoinKycProperties p) {
        if (p == null || isBlank(p.clientId()) || isBlank(p.clientSecret())) return null;
        return new Creds(p.clientId(), p.clientSecret(), p.baseUrl());
    }

    private static Creds credsFrom(CelcoinKybProperties p) {
        if (p == null || isBlank(p.clientId()) || isBlank(p.clientSecret())) return null;
        return new Creds(p.clientId(), p.clientSecret(), p.baseUrl());
    }

    private static Creds credsFrom(CelcoinBackgroundCheckProperties p) {
        if (p == null || isBlank(p.clientId()) || isBlank(p.clientSecret())) return null;
        return new Creds(p.clientId(), p.clientSecret(), p.baseUrl());
    }

    private record Creds(String clientId, String clientSecret, String baseUrl) {
        String cacheKey() {
            return clientId + "@" + (baseUrl == null ? "" : baseUrl);
        }
    }

    private record CachedToken(String token, Instant expiresAt) {}

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn) {}
}
