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
 * IT do {@link CelcoinOAuthTokenProvider}: confirma que o adapter cacheia o token entre chamadas e
 * envia o POST no formato form-urlencoded esperado pela Celcoin.
 */
@SpringBootTest
@ActiveProfiles("dev")
class CelcoinOAuthTokenProviderIT {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.kyc.provider", () -> "celcoin");
        registry.add("app.celcoin.kyc.base-url", () -> wireMock.baseUrl());
        registry.add("app.celcoin.kyc.client-id", () -> "ci");
        registry.add("app.celcoin.kyc.client-secret", () -> "cs");
        registry.add("resilience4j.retry.instances.celcoin-kyc.waitDuration", () -> "10ms");
    }

    @Autowired
    private CelcoinOAuthTokenProvider tokenProvider;

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Test
    void cachaTokenEntreChamadasEnquantoNaoExpira() {
        wireMock.stubFor(post(urlEqualTo("/token"))
                .withRequestBody(containing("grant_type=client_credentials"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"tk-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));

        String t1 = tokenProvider.accessToken(CelcoinOAuthTokenProvider.ProviderKey.KYC);
        String t2 = tokenProvider.accessToken(CelcoinOAuthTokenProvider.ProviderKey.KYC);
        String t3 = tokenProvider.accessToken(CelcoinOAuthTokenProvider.ProviderKey.KYC);

        assertThat(t1).isEqualTo("tk-1");
        assertThat(t2).isEqualTo(t1);
        assertThat(t3).isEqualTo(t1);
        // Apenas 1 chamada ao token endpoint apesar de 3 chamadas a accessToken()
        wireMock.verify(1, postRequestedFor(urlEqualTo("/token")));
    }
}
