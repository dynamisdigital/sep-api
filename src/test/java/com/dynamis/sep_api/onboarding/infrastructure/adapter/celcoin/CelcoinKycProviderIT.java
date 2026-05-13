package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.KycProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoVerificacaoKyc;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaInicioVerificacao;
import com.dynamis.sep_api.onboarding.application.port.out.dto.ResultadoKycProvider;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test do {@link CelcoinKycProvider} com WireMock (ADR 0008). Valida wiring HTTP real:
 * OAuth2 client-credentials, headers de autenticacao Bearer, parsing de resposta, retry em 5xx e
 * propagacao de X-Correlation-Id.
 *
 * <p>{@code app.kyc.provider=celcoin} forca o adapter Celcoin (em vez do Fake default).
 */
@SpringBootTest
@ActiveProfiles("dev")
class CelcoinKycProviderIT {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void celcoinProps(DynamicPropertyRegistry registry) {
        registry.add("app.kyc.provider", () -> "celcoin");
        registry.add("app.celcoin.kyc.base-url", () -> wireMock.baseUrl());
        registry.add("app.celcoin.kyc.client-id", () -> "test-client");
        registry.add("app.celcoin.kyc.client-secret", () -> "test-secret");
        // Acelerar retry no teste de 5xx
        registry.add("resilience4j.retry.instances.celcoin-kyc.waitDuration", () -> "10ms");
        // Evitar que o CB abra durante a suite (teste de 5xx propositalmente falha varias vezes).
        registry.add("resilience4j.circuitbreaker.instances.celcoin-kyc.slidingWindowSize", () -> "100");
        registry.add("resilience4j.circuitbreaker.instances.celcoin-kyc.minimumNumberOfCalls", () -> "100");
    }

    @Autowired
    private KycProvider provider;

    @Autowired
    private io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void stubOAuth() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("celcoin-kyc").reset();
        // Token endpoint: POST /token retorna access_token valido por 1h
        wireMock.stubFor(
                post(urlEqualTo("/token"))
                        .withRequestBody(containing("grant_type=client_credentials"))
                        .withRequestBody(containing("client_id=test-client"))
                        .withRequestBody(containing("client_secret=test-secret"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"access_token\":\"oauth-token-xyz\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    private RequisicaoVerificacaoKyc novaRequisicao() {
        return new RequisicaoVerificacaoKyc(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "52998224725",
                "Joao da Silva",
                LocalDate.of(1990, 1, 1),
                List.of(new RequisicaoVerificacaoKyc.DocumentoMetadados(TipoDocumento.RG, "abc", 100L, "image/jpeg")));
    }

    @Test
    void iniciarVerificacaoUsaBearerOAuthEDevolveIdExterno() {
        wireMock.stubFor(post(urlEqualTo("/verifications"))
                .withHeader("Authorization", equalTo("Bearer oauth-token-xyz"))
                .withRequestBody(matchingJsonPath("$.document_number", equalTo("52998224725")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"verification_id\":\"ext-celcoin-001\",\"status\":\"PROCESSING\"}")));

        RespostaInicioVerificacao resp = provider.iniciarVerificacao(novaRequisicao(), "corr-1");

        assertThat(resp.idVerificacaoExterna()).isEqualTo("ext-celcoin-001");
        assertThat(resp.statusInicial()).isEqualTo("PROCESSING");
    }

    @Test
    void consultarResultadoAPPROVEDDevolveFinalizado() {
        wireMock.stubFor(get(urlEqualTo("/verifications/ext-celcoin-002"))
                .withHeader("Authorization", equalTo("Bearer oauth-token-xyz"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"verification_id\":\"ext-celcoin-002\",\"status\":\"APPROVED\"}")));

        ResultadoKycProvider r = provider.consultarResultado("ext-celcoin-002", "corr-2");

        assertThat(r).isInstanceOf(ResultadoKycProvider.Finalizado.class);
        assertThat(((ResultadoKycProvider.Finalizado) r).statusFinal()).isEqualTo(StatusOnboarding.APROVADO);
    }

    @Test
    void consultarResultadoPROCESSINGDevolveEmAndamento() {
        wireMock.stubFor(get(urlEqualTo("/verifications/ext-celcoin-003"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"verification_id\":\"ext-celcoin-003\",\"status\":\"PROCESSING\"}")));

        ResultadoKycProvider r = provider.consultarResultado("ext-celcoin-003", "corr-3");

        assertThat(r).isInstanceOf(ResultadoKycProvider.EmAndamento.class);
    }

    @Test
    void erro4xxEPropagadoSemRetry() {
        wireMock.stubFor(post(urlEqualTo("/verifications"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"bad request\"}")));

        assertThatThrownBy(() -> provider.iniciarVerificacao(novaRequisicao(), "corr-4"))
                .isInstanceOf(HttpClientErrorException.class);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/verifications")));
    }

    @Test
    void erro5xxAcionaRetryAteMaxAttempts() {
        wireMock.stubFor(post(urlEqualTo("/verifications")).willReturn(serverError()));

        assertThatThrownBy(() -> provider.iniciarVerificacao(novaRequisicao(), "corr-5"))
                .isInstanceOf(HttpServerErrorException.class);

        // maxAttempts = 3 → 3 chamadas ao total
        wireMock.verify(3, postRequestedFor(urlEqualTo("/verifications")));
    }

    @Test
    void correlationIdHeaderEncaminhadoQuandoFornecido() {
        wireMock.stubFor(post(urlEqualTo("/verifications"))
                .withHeader("X-Correlation-Id", matching("corr-6"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"verification_id\":\"ext-c6\",\"status\":\"PROCESSING\"}")));

        RespostaInicioVerificacao resp = provider.iniciarVerificacao(novaRequisicao(), "corr-6");

        assertThat(resp.idVerificacaoExterna()).isEqualTo("ext-c6");
    }

    @Test
    void adapterNaoSobrescreveIdempotencyKeyDoCallerNoMdc() {
        wireMock.stubFor(post(urlEqualTo("/verifications"))
                .withHeader("Idempotency-Key", equalTo("solicitacao-xyz:5"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"verification_id\":\"ext-idem\",\"status\":\"PROCESSING\"}")));

        // Caller (use case) grava chave deterministica no MDC antes da chamada.
        org.slf4j.MDC.put("idempotencyKey", "solicitacao-xyz:5");
        try {
            RespostaInicioVerificacao resp = provider.iniciarVerificacao(novaRequisicao(), "corr-7");
            assertThat(resp.idVerificacaoExterna()).isEqualTo("ext-idem");
        } finally {
            org.slf4j.MDC.remove("idempotencyKey");
        }
    }
}
