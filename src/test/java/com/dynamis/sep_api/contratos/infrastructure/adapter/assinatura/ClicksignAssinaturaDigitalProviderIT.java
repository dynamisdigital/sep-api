package com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura;

import com.dynamis.sep_api.contratos.application.port.out.AssinaturaDigitalProvider;
import com.dynamis.sep_api.contratos.application.port.out.dto.RequisicaoEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.port.out.dto.RespostaEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.port.out.dto.StatusEnvelopeProvider;
import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
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

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test do {@link ClicksignAssinaturaDigitalProvider} com WireMock (ADR 0008). Valida
 * wiring HTTP real: headers Authorization Bearer + Idempotency-Key, parsing das respostas,
 * mapeamento de status, retry em 5xx, download de bytes.
 *
 * <p>{@code app.assinatura.provider=clicksign} forca o adapter real (sobrescreve fake default).
 */
@SpringBootTest
@ActiveProfiles("dev")
class ClicksignAssinaturaDigitalProviderIT {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.assinatura.provider", () -> "clicksign");
        registry.add("app.assinatura.clicksign.base-url", () -> wireMock.baseUrl());
        registry.add("app.assinatura.clicksign.access-token", () -> "test-token-xyz");
        registry.add("app.assinatura.clicksign.webhook.hmac-secret", () -> "secret");
        // Acelerar retry no teste de 5xx
        registry.add("resilience4j.retry.instances.clicksign-assinatura.waitDuration", () -> "10ms");
        // Evitar abertura do CB durante varios 5xx propositais
        registry.add("resilience4j.circuitbreaker.instances.clicksign-assinatura.slidingWindowSize", () -> "100");
        registry.add("resilience4j.circuitbreaker.instances.clicksign-assinatura.minimumNumberOfCalls", () -> "100");
    }

    @Autowired
    private AssinaturaDigitalProvider provider;

    @Autowired
    private io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("clicksign-assinatura").reset();
    }

    @Test
    void enviar_chamaDocumentsEListsComHeadersCorretos() {
        wireMock.stubFor(
                post(urlEqualTo("/api/v1/documents"))
                        .willReturn(
                                aResponse()
                                        .withStatus(201)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {"document":{"key":"doc-key-123","status":"running","updated_at":"2026-05-21T10:00:00-03:00"}}
                                """)));
        wireMock.stubFor(
                post(urlEqualTo("/api/v1/lists")).willReturn(aResponse().withStatus(201)));

        RequisicaoEnvioAssinatura req = req("idemp-1");

        RespostaEnvioAssinatura resp = provider.enviarParaAssinatura("%PDF-1.4 fake".getBytes(), req, "corr-1");

        assertThat(resp.idEnvelopeExterno()).isEqualTo("doc-key-123");
        assertThat(resp.dataEnvio()).isNotNull();
        wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/documents"))
                .withHeader("Authorization", equalTo("Bearer test-token-xyz"))
                .withHeader("Idempotency-Key", equalTo("idemp-1")));
        wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/lists"))
                .withHeader("Authorization", equalTo("Bearer test-token-xyz"))
                .withHeader("Idempotency-Key", equalTo("idemp-1:signer")));
    }

    @Test
    void consultarStatus_mapeiaRunningParaEnviado() {
        wireMock.stubFor(
                get(urlEqualTo("/api/v1/documents/doc-1"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {"document":{"key":"doc-1","status":"running","updated_at":"2026-05-21T10:00:00-03:00"}}
                                """)));

        StatusEnvelopeProvider status = provider.consultarStatus("doc-1");

        assertThat(status.status()).isEqualTo(StatusEnvelope.ENVIADO);
    }

    @Test
    void consultarStatus_mapeiaClosedParaAssinado() {
        wireMock.stubFor(
                get(urlEqualTo("/api/v1/documents/doc-2"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {"document":{"key":"doc-2","status":"closed","updated_at":"2026-05-21T11:00:00-03:00"}}
                                """)));

        assertThat(provider.consultarStatus("doc-2").status()).isEqualTo(StatusEnvelope.ASSINADO);
    }

    @Test
    void consultarStatus_mapeiaRefusedParaRecusado() {
        wireMock.stubFor(
                get(urlEqualTo("/api/v1/documents/doc-3"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {"document":{"key":"doc-3","status":"refused","updated_at":"2026-05-21T12:00:00-03:00"}}
                                """)));

        assertThat(provider.consultarStatus("doc-3").status()).isEqualTo(StatusEnvelope.RECUSADO);
    }

    @Test
    void baixarDocumentoAssinado_retornaBytes() {
        byte[] pdfAssinado = "%PDF-1.4 assinado".getBytes();
        wireMock.stubFor(get(urlEqualTo("/api/v1/documents/doc-4/download"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(pdfAssinado)));

        byte[] bytes = provider.baixarDocumentoAssinado("doc-4");

        assertThat(bytes).isEqualTo(pdfAssinado);
        wireMock.verify(getRequestedFor(urlEqualTo("/api/v1/documents/doc-4/download"))
                .withHeader("Authorization", equalTo("Bearer test-token-xyz")));
    }

    @Test
    void enviar_5xx_aciona_retry() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/documents")).willReturn(serverError()));

        assertThatThrownBy(() -> provider.enviarParaAssinatura(new byte[] {1, 2, 3}, req("idemp-5xx"), "corr-5xx"))
                .isInstanceOf(HttpServerErrorException.class);

        // 3 tentativas configuradas (maxAttempts=3)
        wireMock.verify(3, postRequestedFor(urlEqualTo("/api/v1/documents")));
    }

    @Test
    void enviar_pdfBase64EncodadoNoBody() {
        byte[] pdf = "%PDF-1.4 tiny".getBytes();
        wireMock.stubFor(
                post(urlEqualTo("/api/v1/documents"))
                        .willReturn(
                                aResponse()
                                        .withStatus(201)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {"document":{"key":"doc-encoded","status":"running","updated_at":"2026-05-21T10:00:00-03:00"}}
                                """)));
        wireMock.stubFor(
                post(urlPathMatching("/api/v1/lists.*")).willReturn(aResponse().withStatus(201)));

        provider.enviarParaAssinatura(pdf, req("idemp-encoded"), "corr-encoded");

        wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/documents"))
                .withRequestBody(
                        com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath("$.document.content_base64")));
    }

    private RequisicaoEnvioAssinatura req(String idempotencyKey) {
        return new RequisicaoEnvioAssinatura(
                UUID.randomUUID(), UUID.randomUUID(), "tomador@example.com", "Tomador Teste", idempotencyKey);
    }
}
