package com.dynamis.sep_api.pix.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.StatusTransferenciaPixProvider;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test do {@link CelcoinPixProvider} com WireMock (ADR 0008). Valida OAuth2, Bearer,
 * parsing, mapeamento de status, retry em 5xx e propagacao de 4xx — sem credenciais reais.
 */
@SpringBootTest
@ActiveProfiles("dev")
class CelcoinPixProviderIT {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void celcoinProps(DynamicPropertyRegistry registry) {
        registry.add("app.pix.provider", () -> "celcoin");
        registry.add("app.celcoin.pix.base-url", () -> wireMock.baseUrl());
        registry.add("app.celcoin.pix.client-id", () -> "test-client");
        registry.add("app.celcoin.pix.client-secret", () -> "test-secret");
        registry.add("resilience4j.retry.instances.celcoin-pix.waitDuration", () -> "10ms");
        registry.add("resilience4j.circuitbreaker.instances.celcoin-pix.slidingWindowSize", () -> "100");
        registry.add("resilience4j.circuitbreaker.instances.celcoin-pix.minimumNumberOfCalls", () -> "100");
    }

    @Autowired
    private PixProvider provider;

    @Autowired
    private io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private CelcoinPixOAuthTokenProvider tokenProvider;

    @BeforeEach
    void stubOAuth() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("celcoin-pix").reset();
        tokenProvider.resetCache();
        wireMock.stubFor(post(urlEqualTo("/token"))
                .withRequestBody(containing("grant_type=client_credentials"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"pix-token-xyz\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    private ComandoTransferenciaPix novoComando() {
        return new ComandoTransferenciaPix(new BigDecimal("100.00"), "chave@pix.test", "teste");
    }

    @Test
    void solicitarTransferenciaUsaBearerEMapeiaStatus() {
        wireMock.stubFor(post(urlEqualTo("/pix/transfers"))
                .withHeader("Authorization", equalTo("Bearer pix-token-xyz"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"transfer_id\":\"tx-001\",\"status\":\"PENDING\"}")));

        RespostaTransferenciaPix resp = provider.solicitarTransferencia(novoComando(), "idem-1", "corr-1");

        assertThat(resp.externalId()).isEqualTo("tx-001");
        assertThat(resp.status()).isEqualTo(StatusTransferenciaPixProvider.PENDENTE);
    }

    @Test
    void consultarTransferenciaMapeiaConcluida() {
        wireMock.stubFor(get(urlEqualTo("/pix/transfers/tx-002"))
                .withHeader("Authorization", equalTo("Bearer pix-token-xyz"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"transfer_id\":\"tx-002\",\"status\":\"COMPLETED\"}")));

        RespostaTransferenciaPix resp = provider.consultarTransferencia("tx-002", "corr-2");
        assertThat(resp.status()).isEqualTo(StatusTransferenciaPixProvider.CONCLUIDA);
    }

    @Test
    void erro4xxPropagadoSemRetry() {
        wireMock.stubFor(post(urlEqualTo("/pix/transfers"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"bad\"}")));

        assertThatThrownBy(() -> provider.solicitarTransferencia(novoComando(), "idem-x", "corr-3"))
                .isInstanceOf(HttpClientErrorException.class);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/pix/transfers")));
    }

    @Test
    void erro5xxAcionaRetryAteMaxAttempts() {
        wireMock.stubFor(post(urlEqualTo("/pix/transfers")).willReturn(serverError()));

        assertThatThrownBy(() -> provider.solicitarTransferencia(novoComando(), "idem-y", "corr-4"))
                .isInstanceOf(HttpServerErrorException.class);

        wireMock.verify(3, postRequestedFor(urlEqualTo("/pix/transfers")));
    }

    @Test
    void respostaSemTransferIdLevantaIllegalState() {
        wireMock.stubFor(post(urlEqualTo("/pix/transfers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"PENDING\"}")));

        assertThatThrownBy(() -> provider.solicitarTransferencia(novoComando(), "idem-z", "corr-5"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transfer_id");
    }

    @Test
    void statusDesconhecidoLevantaIllegalState() {
        wireMock.stubFor(post(urlEqualTo("/pix/transfers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"transfer_id\":\"tx-x\",\"status\":\"WAT\"}")));

        assertThatThrownBy(() -> provider.solicitarTransferencia(novoComando(), "idem-w", "corr-6"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("desconhecido");
    }

    @Test
    void idempotencyKeyPropagadaNoHeader() {
        wireMock.stubFor(post(urlEqualTo("/pix/transfers"))
                .withHeader("Idempotency-Key", equalTo("pix:transfer:abc:1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"transfer_id\":\"tx-idem\",\"status\":\"PENDING\"}")));

        RespostaTransferenciaPix resp = provider.solicitarTransferencia(novoComando(), "pix:transfer:abc:1", "corr-7");
        assertThat(resp.externalId()).isEqualTo("tx-idem");
    }

    @Test
    void oauthTokenReusadoEntreChamadas() {
        wireMock.stubFor(post(urlEqualTo("/pix/transfers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"transfer_id\":\"tx-r\",\"status\":\"PENDING\"}")));

        provider.solicitarTransferencia(novoComando(), "idem-a", "corr-8a");
        provider.solicitarTransferencia(novoComando(), "idem-b", "corr-8b");

        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/token")));
    }
}
