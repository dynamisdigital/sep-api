package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.BackgroundCheckProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaPld;
import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.SeveridadePld;
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

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
 * Integration test do {@link CelcoinBackgroundCheckProvider} com WireMock (ADR 0008). Valida
 * wiring HTTP real: OAuth Bearer, consulta consolidada das 4 bases, hit em base bloqueia,
 * retry em 5xx, 4xx propagado, headers Idempotency-Key + X-Correlation-Id propagados.
 */
@SpringBootTest
@ActiveProfiles("dev")
class CelcoinBackgroundCheckProviderIT {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void celcoinProps(DynamicPropertyRegistry registry) {
        registry.add("app.pld.provider", () -> "celcoin");
        registry.add("app.celcoin.background-check.base-url", wireMock::baseUrl);
        registry.add("app.celcoin.background-check.client-id", () -> "test-client");
        registry.add("app.celcoin.background-check.client-secret", () -> "test-secret");
        // KYC + KYB fake: OAuth resolver deve cair pra background-check.
        registry.add("resilience4j.retry.instances.celcoin-background-check.waitDuration", () -> "10ms");
        registry.add("resilience4j.circuitbreaker.instances.celcoin-background-check.slidingWindowSize", () -> "100");
        registry.add(
                "resilience4j.circuitbreaker.instances.celcoin-background-check.minimumNumberOfCalls", () -> "100");
    }

    @Autowired
    private BackgroundCheckProvider provider;

    @Autowired
    private io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void stubOAuth() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("celcoin-background-check").reset();
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

    private RequisicaoPld novaRequisicaoPessoa() {
        return RequisicaoPld.comBasesObrigatorias(UUID.randomUUID(), AlvoPld.PESSOA, "Joao", "52998224725");
    }

    private RequisicaoPld novaRequisicaoEmpresa() {
        return RequisicaoPld.comBasesObrigatorias(UUID.randomUUID(), AlvoPld.EMPRESA, "ACME LTDA", "11222333000181");
    }

    @Test
    void consultarPessoaLimpaEm4BasesNaoRetornaHits() {
        wireMock.stubFor(
                post(urlEqualTo("/background-check"))
                        .withHeader("Authorization", equalTo("Bearer oauth-token-xyz"))
                        // DoD 7.4: as 4 bases obrigatorias devem estar no payload.
                        .withRequestBody(matchingJsonPath("$.databases[?(@ == 'COAF')]"))
                        .withRequestBody(matchingJsonPath("$.databases[?(@ == 'OFAC')]"))
                        .withRequestBody(matchingJsonPath("$.databases[?(@ == 'INTERPOL')]"))
                        .withRequestBody(matchingJsonPath("$.databases[?(@ == 'MTE')]"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {
                                  "results": [
                                    {"database": "COAF", "hit": false},
                                    {"database": "OFAC", "hit": false},
                                    {"database": "INTERPOL", "hit": false},
                                    {"database": "MTE", "hit": false}
                                  ]
                                }
                                """)));

        RespostaPld resp = provider.consultarPessoa(novaRequisicaoPessoa(), "corr-1");

        assertThat(resp.limpo()).isTrue();
        assertThat(resp.hits()).isEmpty();
    }

    @Test
    void consultarEmpresaHitEmOfacBlqueiaERetornaSeveridadeAlta() {
        wireMock.stubFor(
                post(urlEqualTo("/background-check"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {
                                  "results": [
                                    {"database": "COAF", "hit": false},
                                    {"database": "OFAC", "hit": true, "reason": "Sancao",
                                     "severity": "HIGH", "inclusion_date": "2024-01-01"},
                                    {"database": "INTERPOL", "hit": false},
                                    {"database": "MTE", "hit": false}
                                  ]
                                }
                                """)));

        RespostaPld resp = provider.consultarEmpresa(novaRequisicaoEmpresa(), "corr-2");

        assertThat(resp.limpo()).isFalse();
        assertThat(resp.hits()).hasSize(1);
        assertThat(resp.hits().get(0).base()).isEqualTo(BasePld.OFAC);
        assertThat(resp.hits().get(0).severidade()).isEqualTo(SeveridadePld.ALTA);
        assertThat(resp.hits().get(0).motivo()).isEqualTo("Sancao");
    }

    @Test
    void erro4xxPropagadoSemRetry() {
        wireMock.stubFor(post(urlEqualTo("/background-check"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"bad request\"}")));

        assertThatThrownBy(() -> provider.consultarPessoa(novaRequisicaoPessoa(), "corr-3"))
                .isInstanceOf(HttpClientErrorException.class);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/background-check")));
    }

    @Test
    void erro5xxAcionaRetryAteMaxAttempts() {
        wireMock.stubFor(post(urlEqualTo("/background-check")).willReturn(serverError()));

        assertThatThrownBy(() -> provider.consultarEmpresa(novaRequisicaoEmpresa(), "corr-4"))
                .isInstanceOf(HttpServerErrorException.class);

        wireMock.verify(3, postRequestedFor(urlEqualTo("/background-check")));
    }

    @Test
    void correlationIdHeaderPropagado() {
        wireMock.stubFor(post(urlEqualTo("/background-check"))
                .withHeader("X-Correlation-Id", matching("corr-pld-5"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"results\":[]}")));

        RespostaPld resp = provider.consultarPessoa(novaRequisicaoPessoa(), "corr-pld-5");

        assertThat(resp.limpo()).isTrue();
    }

    @Test
    void idempotencyKeyDoMdcPropagada() {
        wireMock.stubFor(post(urlEqualTo("/background-check"))
                .withHeader("Idempotency-Key", equalTo("solicitacao-xyz:pld:1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"results\":[]}")));

        org.slf4j.MDC.put("idempotencyKey", "solicitacao-xyz:pld:1");
        try {
            RespostaPld resp = provider.consultarPessoa(novaRequisicaoPessoa(), "corr-pld-6");
            assertThat(resp.limpo()).isTrue();
        } finally {
            org.slf4j.MDC.remove("idempotencyKey");
        }
    }
}
