package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.KybProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoKyb;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaKyb;
import com.dynamis.sep_api.onboarding.domain.vo.SituacaoCadastral;
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

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test do {@link CelcoinKybProvider} com WireMock (ADR 0008). Valida wiring HTTP
 * real: OAuth2 Bearer, parsing de resposta, retry em 5xx, 4xx propagado sem retry.
 */
@SpringBootTest
@ActiveProfiles("dev")
class CelcoinKybProviderIT {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void celcoinProps(DynamicPropertyRegistry registry) {
        registry.add("app.kyb.provider", () -> "celcoin");
        registry.add("app.celcoin.kyb.base-url", wireMock::baseUrl);
        registry.add("app.celcoin.kyb.client-id", () -> "test-client");
        registry.add("app.celcoin.kyb.client-secret", () -> "test-secret");
        // OAuth provider compartilhado consulta /token a partir de app.celcoin.kyc.base-url.
        registry.add("app.celcoin.kyc.base-url", wireMock::baseUrl);
        registry.add("app.celcoin.kyc.client-id", () -> "test-client");
        registry.add("app.celcoin.kyc.client-secret", () -> "test-secret");
        // Acelerar retry e evitar abrir CB durante a suite
        registry.add("resilience4j.retry.instances.celcoin-kyb.waitDuration", () -> "10ms");
        registry.add("resilience4j.circuitbreaker.instances.celcoin-kyb.slidingWindowSize", () -> "100");
        registry.add("resilience4j.circuitbreaker.instances.celcoin-kyb.minimumNumberOfCalls", () -> "100");
    }

    @Autowired
    private KybProvider provider;

    @Autowired
    private io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void stubOAuth() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("celcoin-kyb").reset();
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

    private RequisicaoKyb novaRequisicao() {
        return new RequisicaoKyb(UUID.randomUUID(), UUID.randomUUID(), "11222333000181", "ACME LTDA", List.of());
    }

    @Test
    void consultarCnpjAtivaParseiaSituacaoERepresentantes() {
        wireMock.stubFor(
                post(urlEqualTo("/companies"))
                        .withHeader("Authorization", equalTo("Bearer oauth-token-xyz"))
                        .withRequestBody(matchingJsonPath("$.tax_id", equalTo("11222333000181")))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {
                                  "registration_status": "ACTIVE",
                                  "legal_name": "ACME Industria LTDA",
                                  "trade_name": "ACME",
                                  "primary_cnae": "62.01-5-01",
                                  "secondary_cnaes": "62.09-1-00",
                                  "share_capital": 1000000.50,
                                  "opening_date": "2010-01-15",
                                  "legal_representatives": [
                                    {"full_name": "Joao Silva", "document_number": "52998224725", "role": "CEO"}
                                  ]
                                }
                                """)));

        RespostaKyb resp = provider.consultarCnpj(novaRequisicao(), "corr-1");

        assertThat(resp.situacaoCadastral()).isEqualTo(SituacaoCadastral.ATIVA);
        assertThat(resp.razaoSocial()).isEqualTo("ACME Industria LTDA");
        assertThat(resp.cnaePrincipal()).isEqualTo("62.01-5-01");
        assertThat(resp.representantes()).hasSize(1);
        assertThat(resp.representantes().get(0).cpf()).isEqualTo("52998224725");
        assertThat(resp.representantes().get(0).cargo()).isEqualTo("CEO");
    }

    @Test
    void consultarCnpjSuspensaMapeadaCorretamente() {
        wireMock.stubFor(post(urlEqualTo("/companies"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"registration_status\":\"SUSPENDED\",\"legal_representatives\":[]}")));

        RespostaKyb resp = provider.consultarCnpj(novaRequisicao(), "corr-2");

        assertThat(resp.situacaoCadastral()).isEqualTo(SituacaoCadastral.SUSPENSA);
        assertThat(resp.representantes()).isEmpty();
    }

    @Test
    void erro4xxPropagadoSemRetry() {
        wireMock.stubFor(post(urlEqualTo("/companies"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"bad request\"}")));

        assertThatThrownBy(() -> provider.consultarCnpj(novaRequisicao(), "corr-3"))
                .isInstanceOf(HttpClientErrorException.class);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/companies")));
    }

    @Test
    void erro5xxAcionaRetryAteMaxAttempts() {
        wireMock.stubFor(post(urlEqualTo("/companies")).willReturn(serverError()));

        assertThatThrownBy(() -> provider.consultarCnpj(novaRequisicao(), "corr-4"))
                .isInstanceOf(HttpServerErrorException.class);

        wireMock.verify(3, postRequestedFor(urlEqualTo("/companies")));
    }
}
