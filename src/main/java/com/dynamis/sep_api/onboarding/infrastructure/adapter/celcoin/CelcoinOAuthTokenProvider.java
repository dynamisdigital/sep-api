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

/**
 * OAuth2 client-credentials para Celcoin. Cacheia o {@code access_token} ate proximo
 * vencimento — minus folga de 30s para evitar reuso em borda de expiracao.
 *
 * <p>Endpoint default Celcoin: {@code POST {token-url}} com {@code grant_type=client_credentials}.
 * Configuravel via {@code app.celcoin.kyc.token-url}; quando omitido, usa
 * {@code <base-url>/token}. Em producao a URL real costuma ser separada (ex.:
 * {@code https://api-onboarding.celcoin.dev/v5/token/oauth}).
 *
 * <p>Concurrente: sincronizado em {@code this} — chamadas simultaneas reusam o token cacheado.
 */
@Component
@ConditionalOnExpression("'${app.kyc.provider:fake}'.equals('celcoin') "
        + "or '${app.kyb.provider:fake}'.equals('celcoin') "
        + "or '${app.pld.provider:fake}'.equals('celcoin')")
public class CelcoinOAuthTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(CelcoinOAuthTokenProvider.class);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final CelcoinKycProperties kycProperties;
    private final CelcoinKybProperties kybProperties;
    private final CelcoinBackgroundCheckProperties backgroundCheckProperties;
    private final RestClient tokenClient;

    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;

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
     * Devolve um access token valido. Refaz a chamada de token quando o cache expirou (com folga
     * de {@link #CLOCK_SKEW}).
     */
    public synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt.minus(CLOCK_SKEW))) {
            return cachedToken;
        }
        renovar();
        return cachedToken;
    }

    private void renovar() {
        Creds creds = resolverCreds();
        if (creds == null) {
            throw new IllegalStateException(
                    "Credenciais Celcoin ausentes: configure app.celcoin.kyc.* ou app.celcoin.kyb.* "
                            + "(client-id e client-secret)");
        }
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
            this.cachedToken = response.accessToken();
            this.expiresAt = Instant.now().plusSeconds(expiresInSec);
            log.info("Celcoin OAuth token renovado, expira em {}s", expiresInSec);
        } catch (RestClientResponseException ex) {
            // Nao logar body — pode conter detalhes/secret.
            log.warn(
                    "Falha ao renovar OAuth Celcoin status={}",
                    ex.getStatusCode().value());
            throw ex;
        }
    }

    private String tokenUrl(Creds creds) {
        String base = creds.baseUrl();
        if (base == null) base = "";
        return base.endsWith("/") ? base + "token" : base + "/token";
    }

    /**
     * Resolve credenciais OAuth + baseUrl como uma unica unidade. Prefere o bloco que tem
     * client-id + client-secret preenchidos. Ordem de prioridade: KYC -> KYB -> Background Check.
     * Retorna {@code null} quando nenhum bloco tem credenciais — caller lanca IllegalState.
     */
    private Creds resolverCreds() {
        if (!isBlank(kycProperties.clientId()) && !isBlank(kycProperties.clientSecret())) {
            return new Creds(kycProperties.clientId(), kycProperties.clientSecret(), kycProperties.baseUrl());
        }
        if (kybProperties != null && !isBlank(kybProperties.clientId()) && !isBlank(kybProperties.clientSecret())) {
            return new Creds(kybProperties.clientId(), kybProperties.clientSecret(), kybProperties.baseUrl());
        }
        if (backgroundCheckProperties != null
                && !isBlank(backgroundCheckProperties.clientId())
                && !isBlank(backgroundCheckProperties.clientSecret())) {
            return new Creds(
                    backgroundCheckProperties.clientId(),
                    backgroundCheckProperties.clientSecret(),
                    backgroundCheckProperties.baseUrl());
        }
        return null;
    }

    private record Creds(String clientId, String clientSecret, String baseUrl) {}

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn) {}
}
