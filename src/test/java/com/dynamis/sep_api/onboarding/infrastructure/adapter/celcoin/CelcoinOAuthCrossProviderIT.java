package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante isolamento de credenciais OAuth entre adapters Celcoin. Cenario: KYC, KYB e PLD todos
 * em modo Celcoin com credenciais diferentes — cada chamada {@code accessToken(providerKey)}
 * deve usar EXCLUSIVAMENTE o bloco do proprio provider, sem cair pro KYC por priorizacao
 * cega.
 */
@SpringBootTest
@ActiveProfiles("dev")
class CelcoinOAuthCrossProviderIT {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.kyc.provider", () -> "celcoin");
        registry.add("app.kyb.provider", () -> "celcoin");
        registry.add("app.pld.provider", () -> "celcoin");
        registry.add("app.celcoin.kyc.base-url", wireMock::baseUrl);
        registry.add("app.celcoin.kyc.client-id", () -> "kyc-id");
        registry.add("app.celcoin.kyc.client-secret", () -> "kyc-secret");
        registry.add("app.celcoin.kyb.base-url", wireMock::baseUrl);
        registry.add("app.celcoin.kyb.client-id", () -> "kyb-id");
        registry.add("app.celcoin.kyb.client-secret", () -> "kyb-secret");
        registry.add("app.celcoin.background-check.base-url", wireMock::baseUrl);
        registry.add("app.celcoin.background-check.client-id", () -> "pld-id");
        registry.add("app.celcoin.background-check.client-secret", () -> "pld-secret");
    }

    @Autowired
    private CelcoinOAuthTokenProvider tokenProvider;

    @BeforeEach
    void reset() {
        wireMock.resetAll();
        tokenProvider.resetCache();
        // Stub /token retorna access_token marcado com o client_id recebido, pra confirmar isolamento.
        wireMock.stubFor(post(urlEqualTo("/token"))
                .withRequestBody(containing("client_id=kyc-id"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"tk-kyc\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        wireMock.stubFor(post(urlEqualTo("/token"))
                .withRequestBody(containing("client_id=kyb-id"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"tk-kyb\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        wireMock.stubFor(post(urlEqualTo("/token"))
                .withRequestBody(containing("client_id=pld-id"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"tk-pld\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    @Test
    void cadaProviderKeyResolveSuaPropriaCredencialEToken() {
        String tokenKyc = tokenProvider.accessToken(CelcoinOAuthTokenProvider.ProviderKey.KYC);
        String tokenKyb = tokenProvider.accessToken(CelcoinOAuthTokenProvider.ProviderKey.KYB);
        String tokenPld = tokenProvider.accessToken(CelcoinOAuthTokenProvider.ProviderKey.PLD);

        assertThat(tokenKyc).isEqualTo("tk-kyc");
        assertThat(tokenKyb).isEqualTo("tk-kyb");
        assertThat(tokenPld).isEqualTo("tk-pld");

        wireMock.verify(1, postRequestedFor(urlEqualTo("/token")).withRequestBody(containing("client_id=kyc-id")));
        wireMock.verify(1, postRequestedFor(urlEqualTo("/token")).withRequestBody(containing("client_id=kyb-id")));
        wireMock.verify(1, postRequestedFor(urlEqualTo("/token")).withRequestBody(containing("client_id=pld-id")));
    }

    @Test
    void cacheIsoladoPorCredencial() {
        tokenProvider.accessToken(CelcoinOAuthTokenProvider.ProviderKey.KYC);
        tokenProvider.accessToken(CelcoinOAuthTokenProvider.ProviderKey.KYC);
        tokenProvider.accessToken(CelcoinOAuthTokenProvider.ProviderKey.KYB);
        tokenProvider.accessToken(CelcoinOAuthTokenProvider.ProviderKey.KYB);
        tokenProvider.accessToken(CelcoinOAuthTokenProvider.ProviderKey.PLD);

        // Apenas 1 chamada por client_id mesmo com chamadas repetidas pela mesma key.
        wireMock.verify(1, postRequestedFor(urlEqualTo("/token")).withRequestBody(containing("client_id=kyc-id")));
        wireMock.verify(1, postRequestedFor(urlEqualTo("/token")).withRequestBody(containing("client_id=kyb-id")));
        wireMock.verify(1, postRequestedFor(urlEqualTo("/token")).withRequestBody(containing("client_id=pld-id")));
    }
}
