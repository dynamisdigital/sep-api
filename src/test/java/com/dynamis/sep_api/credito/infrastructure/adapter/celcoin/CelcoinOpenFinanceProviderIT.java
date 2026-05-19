package com.dynamis.sep_api.credito.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.credito.application.port.out.OpenFinanceProvider;
import com.dynamis.sep_api.credito.application.port.out.dto.MovimentacaoConsolidada;
import com.dynamis.sep_api.credito.application.port.out.dto.RequisicaoConsentimento;
import com.dynamis.sep_api.credito.application.port.out.dto.RespostaConsentimento;
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
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test do {@link CelcoinOpenFinanceProvider} com WireMock (ADR 0008). Valida OAuth2,
 * Bearer headers, parsing, retry em 5xx e propagacao de erros 4xx.
 */
@SpringBootTest
@ActiveProfiles("dev")
class CelcoinOpenFinanceProviderIT {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void celcoinProps(DynamicPropertyRegistry registry) {
        registry.add("app.open-finance.provider", () -> "celcoin");
        registry.add("app.celcoin.open-finance.base-url", () -> wireMock.baseUrl());
        registry.add("app.celcoin.open-finance.client-id", () -> "test-client");
        registry.add("app.celcoin.open-finance.client-secret", () -> "test-secret");
        registry.add("resilience4j.retry.instances.celcoin-open-finance.waitDuration", () -> "10ms");
        registry.add("resilience4j.circuitbreaker.instances.celcoin-open-finance.slidingWindowSize", () -> "100");
        registry.add("resilience4j.circuitbreaker.instances.celcoin-open-finance.minimumNumberOfCalls", () -> "100");
    }

    @Autowired
    private OpenFinanceProvider provider;

    @Autowired
    private io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private CelcoinOpenFinanceOAuthTokenProvider tokenProvider;

    @BeforeEach
    void stubOAuth() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("celcoin-open-finance").reset();
        tokenProvider.resetCache();
        wireMock.stubFor(post(urlEqualTo("/token"))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("client_id=test-client"))
                .withRequestBody(containing("client_secret=test-secret"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"access_token\":\"of-token-xyz\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    private RequisicaoConsentimento novaRequisicao() {
        return new RequisicaoConsentimento(UUID.randomUUID(), UUID.randomUUID(), "52998224725", "https://sep/cb");
    }

    @Test
    void iniciarConsentimentoUsaBearerEDevolveIdExterno() {
        wireMock.stubFor(
                post(urlEqualTo("/consents"))
                        .withHeader("Authorization", equalTo("Bearer of-token-xyz"))
                        .withRequestBody(matchingJsonPath("$.documento", equalTo("52998224725")))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"consent_id\":\"ext-of-001\",\"authorization_url\":\"https://celcoin/authz/001\",\"expires_at\":\"2026-06-18T12:00:00-03:00\"}")));

        RespostaConsentimento resp = provider.iniciarConsentimento(novaRequisicao(), "corr-1");

        assertThat(resp.idExterno()).isEqualTo("ext-of-001");
        assertThat(resp.urlAutorizacao()).isEqualTo("https://celcoin/authz/001");
        assertThat(resp.dataExpiracao()).isNotNull();
    }

    @Test
    void consultarMovimentacaoParseSnapshotConsolidado() {
        wireMock.stubFor(
                get(urlEqualTo("/consents/ext-of-002/movements"))
                        .withHeader("Authorization", equalTo("Bearer of-token-xyz"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"consent_id\":\"ext-of-002\",\"media_entradas_mensal\":15000.50,\"media_saidas_mensal\":9000.00,\"saldo_medio\":4500.00,\"meses_avaliados\":12}")));

        MovimentacaoConsolidada mov = provider.consultarMovimentacao("ext-of-002", "corr-2");

        assertThat(mov.mediaEntradasMensal()).isEqualByComparingTo(new BigDecimal("15000.50"));
        assertThat(mov.mediaSaidasMensal()).isEqualByComparingTo(new BigDecimal("9000.00"));
        assertThat(mov.saldoMedio()).isEqualByComparingTo(new BigDecimal("4500.00"));
        assertThat(mov.numeroMesesAvaliados()).isEqualTo(12);
        assertThat(mov.payloadConsolidado()).contains("ext-of-002");
    }

    @Test
    void erro4xxEPropagadoSemRetry() {
        wireMock.stubFor(post(urlEqualTo("/consents"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"bad request\"}")));

        assertThatThrownBy(() -> provider.iniciarConsentimento(novaRequisicao(), "corr-3"))
                .isInstanceOf(HttpClientErrorException.class);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/consents")));
    }

    @Test
    void erro5xxAcionaRetryAteMaxAttempts() {
        wireMock.stubFor(post(urlEqualTo("/consents")).willReturn(serverError()));

        assertThatThrownBy(() -> provider.iniciarConsentimento(novaRequisicao(), "corr-4"))
                .isInstanceOf(HttpServerErrorException.class);

        wireMock.verify(3, postRequestedFor(urlEqualTo("/consents")));
    }

    @Test
    void responseSemConsentIdLevantaIllegalState() {
        wireMock.stubFor(post(urlEqualTo("/consents"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"authorization_url\":\"https://celcoin/authz/x\"}")));

        assertThatThrownBy(() -> provider.iniciarConsentimento(novaRequisicao(), "corr-noid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consent_id");
    }

    @Test
    void responseSemAuthorizationUrlLevantaIllegalState() {
        wireMock.stubFor(post(urlEqualTo("/consents"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"consent_id\":\"ext-x\"}")));

        assertThatThrownBy(() -> provider.iniciarConsentimento(novaRequisicao(), "corr-nourl"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authorization_url");
    }

    @Test
    void idempotencyKeyPropagadaQuandoCallerSetaMdc() {
        wireMock.stubFor(
                post(urlEqualTo("/consents"))
                        .withHeader("Idempotency-Key", equalTo("open-finance:consent:prop-xyz:1"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"consent_id\":\"ext-idem\",\"authorization_url\":\"https://celcoin/authz/idem\",\"expires_at\":\"2026-06-18T12:00:00-03:00\"}")));

        org.slf4j.MDC.put("idempotencyKey", "open-finance:consent:prop-xyz:1");
        try {
            RespostaConsentimento resp = provider.iniciarConsentimento(novaRequisicao(), "corr-idem");
            assertThat(resp.idExterno()).isEqualTo("ext-idem");
        } finally {
            org.slf4j.MDC.remove("idempotencyKey");
        }
    }

    @Test
    void oauthTokenReusadoEntreChamadas() {
        wireMock.stubFor(
                post(urlEqualTo("/consents"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"consent_id\":\"ext-of-x\",\"authorization_url\":\"https://celcoin/authz/x\",\"expires_at\":\"2026-06-18T12:00:00-03:00\"}")));

        provider.iniciarConsentimento(novaRequisicao(), "corr-5a");
        provider.iniciarConsentimento(novaRequisicao(), "corr-5b");

        // Token requisitado UMA vez apenas (cache funciona).
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/token")));
    }
}
