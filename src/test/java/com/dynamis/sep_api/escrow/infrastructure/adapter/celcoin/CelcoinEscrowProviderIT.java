package com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.escrow.application.port.out.EscrowProvider;
import com.dynamis.sep_api.escrow.application.port.out.dto.ComandoCriarContaEscrow;
import com.dynamis.sep_api.escrow.application.port.out.dto.ComandoCriarWallet;
import com.dynamis.sep_api.escrow.application.port.out.dto.RespostaContaEscrow;
import com.dynamis.sep_api.escrow.application.port.out.dto.RespostaWallet;
import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.escrow.domain.vo.TipoWallet;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpServerErrorException;

import java.math.BigDecimal;
import java.util.UUID;

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
 * Integration test do {@link CelcoinEscrowProvider} com WireMock (ADR 0008). Valida OAuth2, Bearer,
 * criar/consultar conta e wallet, consultar saldo, mapeamento de status e retry em 5xx.
 */
@SpringBootTest
@ActiveProfiles("dev")
class CelcoinEscrowProviderIT {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void celcoinProps(DynamicPropertyRegistry registry) {
        registry.add("app.escrow.provider", () -> "celcoin");
        registry.add("app.celcoin.escrow.base-url", () -> wireMock.baseUrl());
        registry.add("app.celcoin.escrow.client-id", () -> "test-client");
        registry.add("app.celcoin.escrow.client-secret", () -> "test-secret");
        registry.add("resilience4j.retry.instances.celcoin-escrow.waitDuration", () -> "10ms");
        registry.add("resilience4j.circuitbreaker.instances.celcoin-escrow.slidingWindowSize", () -> "100");
        registry.add("resilience4j.circuitbreaker.instances.celcoin-escrow.minimumNumberOfCalls", () -> "100");
    }

    @Autowired
    private EscrowProvider provider;

    @Autowired
    private io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private CelcoinEscrowOAuthTokenProvider tokenProvider;

    @BeforeEach
    void stubOAuth() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("celcoin-escrow").reset();
        tokenProvider.resetCache();
        wireMock.stubFor(post(urlEqualTo("/token"))
                .withRequestBody(containing("grant_type=client_credentials"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"escrow-token-xyz\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    @Test
    void criarContaEscrowUsaBearerEMapeiaStatus() {
        wireMock.stubFor(post(urlEqualTo("/escrow/accounts"))
                .withHeader("Authorization", equalTo("Bearer escrow-token-xyz"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"account_id\":\"acc-001\",\"status\":\"ACTIVE\"}")));

        RespostaContaEscrow resp = provider.criarContaEscrow(new ComandoCriarContaEscrow("SEP"), "idem-1", "corr-1");

        assertThat(resp.externalId()).isEqualTo("acc-001");
        assertThat(resp.status()).isEqualTo(StatusContaEscrow.ATIVA);
    }

    @Test
    void consultarContaEscrowMapeiaStatus() {
        wireMock.stubFor(get(urlEqualTo("/escrow/accounts/acc-002"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"account_id\":\"acc-002\",\"status\":\"BLOCKED\"}")));

        assertThat(provider.consultarContaEscrow("acc-002", "corr-2").status())
                .isEqualTo(StatusContaEscrow.BLOQUEADA);
    }

    @Test
    void criarWalletDevolveIdESaldo() {
        wireMock.stubFor(post(urlEqualTo("/escrow/wallets"))
                .withHeader("Authorization", equalTo("Bearer escrow-token-xyz"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"wallet_id\":\"w-001\",\"balance\":0.00}")));

        RespostaWallet resp = provider.criarWallet(
                new ComandoCriarWallet("acc-001", UUID.randomUUID(), TipoWallet.PROPOSTA), "idem-1", "corr-3");

        assertThat(resp.externalId()).isEqualTo("w-001");
        assertThat(resp.saldo()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void consultarSaldoDevolveValor() {
        wireMock.stubFor(get(urlEqualTo("/escrow/wallets/w-002/balance"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"wallet_id\":\"w-002\",\"balance\":1500.50}")));

        assertThat(provider.consultarSaldo("w-002", "corr-4")).isEqualByComparingTo(new BigDecimal("1500.50"));
    }

    @Test
    void erro5xxAcionaRetryAteMaxAttempts() {
        wireMock.stubFor(post(urlEqualTo("/escrow/accounts")).willReturn(serverError()));

        assertThatThrownBy(() -> provider.criarContaEscrow(new ComandoCriarContaEscrow("SEP"), "idem-x", "corr-5"))
                .isInstanceOf(HttpServerErrorException.class);

        wireMock.verify(3, postRequestedFor(urlEqualTo("/escrow/accounts")));
    }

    @Test
    void statusContaDesconhecidoLevantaIllegalState() {
        wireMock.stubFor(post(urlEqualTo("/escrow/accounts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"account_id\":\"acc-x\",\"status\":\"WAT\"}")));

        assertThatThrownBy(() -> provider.criarContaEscrow(new ComandoCriarContaEscrow("SEP"), "idem-w", "corr-6"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("desconhecido");
    }

    @Test
    void oauthTokenReusadoEntreChamadas() {
        wireMock.stubFor(post(urlEqualTo("/escrow/accounts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"account_id\":\"acc-r\",\"status\":\"ACTIVE\"}")));

        provider.criarContaEscrow(new ComandoCriarContaEscrow("SEP"), "idem-a", "corr-7a");
        provider.criarContaEscrow(new ComandoCriarContaEscrow("SEP"), "idem-b", "corr-7b");

        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/token")));
    }
}
