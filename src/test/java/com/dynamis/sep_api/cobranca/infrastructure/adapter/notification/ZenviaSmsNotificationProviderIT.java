package com.dynamis.sep_api.cobranca.infrastructure.adapter.notification;

import com.dynamis.sep_api.cobranca.application.port.out.NotificationProvider;
import com.dynamis.sep_api.cobranca.application.port.out.dto.Notificacao;
import com.dynamis.sep_api.cobranca.application.port.out.dto.ResultadoNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do {@link ZenviaSmsNotificationProvider} com WireMock (ADR 0008).
 *
 * <p>{@code app.notificacoes.provider=smtp-zenvia} forca o adapter real (em vez do
 * {@link LogNotificationProvider} default do perfil test).
 */
@SpringBootTest
@ActiveProfiles("dev")
class ZenviaSmsNotificationProviderIT {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void zenviaProps(DynamicPropertyRegistry registry) {
        registry.add("app.notificacoes.provider", () -> "smtp-zenvia");
        registry.add("app.notificacoes.zenvia.base-url", wireMock::baseUrl);
        registry.add("app.notificacoes.zenvia.api-token", () -> "test-zenvia-token");
        registry.add("app.notificacoes.zenvia.from", () -> "SEP-TEST");
        // Acelerar retry no teste de 5xx.
        registry.add("resilience4j.retry.instances.zenvia-sms.waitDuration", () -> "10ms");
        // Evitar abertura do CB no teste de erro.
        registry.add("resilience4j.circuitbreaker.instances.zenvia-sms.slidingWindowSize", () -> "100");
        registry.add("resilience4j.circuitbreaker.instances.zenvia-sms.minimumNumberOfCalls", () -> "100");
        // Spring tenta sobrescrever JavaMailSender em prod — fornecer mail.host evita ConfigurationError.
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "25");
    }

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("zenviaSmsNotificationProvider")
    private NotificationProvider zenviaProvider;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetMocks() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("zenvia-sms").reset();
    }

    @Test
    void enviar_sms_201_retornaSucessoComIdExterno() {
        wireMock.stubFor(post(urlEqualTo("/v2/channels/sms/messages"))
                .withHeader("X-API-TOKEN", equalTo("test-zenvia-token"))
                .withRequestBody(matchingJsonPath("$.from", equalTo("SEP-TEST")))
                .withRequestBody(matchingJsonPath("$.to", equalTo("+5511999999999")))
                .withRequestBody(matchingJsonPath("$.contents[0].type", equalTo("text")))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"msg-zenvia-123\"}")));

        ResultadoNotificacao r = zenviaProvider.enviar(new Notificacao(
                CanalNotificacao.SMS,
                "+5511999999999",
                "cobranca-lembrete",
                Map.of("numeroParcela", 1, "dataVencimento", "10/06/2026", "valor", "R$ 100,00"),
                "corr-it-1"));

        assertThat(r.status()).isEqualTo(StatusEventoCobranca.SUCESSO);
        assertThat(r.providerNome()).isEqualTo("zenvia");
        assertThat(r.idExterno()).isEqualTo("msg-zenvia-123");

        wireMock.verify(postRequestedFor(urlEqualTo("/v2/channels/sms/messages")));
    }

    @Test
    void enviar_sms_5xx_aposRetry_retornaFalha() {
        wireMock.stubFor(post(urlEqualTo("/v2/channels/sms/messages")).willReturn(serverError()));

        ResultadoNotificacao r = zenviaProvider.enviar(new Notificacao(
                CanalNotificacao.SMS,
                "+5511999999999",
                "cobranca-firme",
                Map.of("numeroParcela", 2, "diasAtraso", 15),
                "corr-it-2"));

        assertThat(r.status()).isEqualTo(StatusEventoCobranca.FALHA);
        assertThat(r.mensagemTecnica()).contains("zenvia http 500");

        // Confirma que houve retry (3 tentativas = config padrao do zenvia-sms).
        wireMock.verify(3, postRequestedFor(urlEqualTo("/v2/channels/sms/messages")));
    }

    @Test
    void enviar_telefoneInvalido_naoChamaZenvia() {
        ResultadoNotificacao r = zenviaProvider.enviar(
                new Notificacao(CanalNotificacao.SMS, "abc", "cobranca-lembrete", Map.of(), "corr-it-3"));

        assertThat(r.status()).isEqualTo(StatusEventoCobranca.FALHA);
        assertThat(r.mensagemTecnica()).contains("telefone");
        wireMock.verify(0, postRequestedFor(urlEqualTo("/v2/channels/sms/messages")));
    }

    @Test
    void enviar_canalEmail_retornaFalhaSemBaterNoProvider() {
        ResultadoNotificacao r = zenviaProvider.enviar(
                new Notificacao(CanalNotificacao.EMAIL, "x@y.com", "cobranca-amigavel", Map.of(), "corr-it-4"));

        assertThat(r.status()).isEqualTo(StatusEventoCobranca.FALHA);
        wireMock.verify(0, postRequestedFor(urlEqualTo("/v2/channels/sms/messages")));
    }
}
