package com.dynamis.sep_api.pix.web;

import com.dynamis.sep_api.pix.domain.model.PixWebhookEvent;
import com.dynamis.sep_api.pix.domain.vo.StatusPixWebhookEvent;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixWebhookEventRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do webhook Pix (Sprint 19 Task 19.5). Valida HMAC, idempotencia por event_id,
 * criacao de recebimento, minimizacao de dados (so hash persistido) e roteamento por tipo. Usa o
 * {@code FakePixProvider} (default em test) — sem credenciais Celcoin.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PixWebhookIT {

    private static final String WEBHOOK_SECRET = "dev-pix-webhook-secret-change-me";
    private static final String URL = "/api/v1/webhooks/celcoin/pix";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.pix.provider", () -> "fake");
        registry.add("app.webhooks.secrets.celcoin-pix", () -> WEBHOOK_SECRET);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private PixWebhookEventRepository webhookEventRepository;

    @Autowired
    private PixRecebimentoRepository recebimentoRepository;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
    }

    private static String recebimentoPayload(String eventId, String endToEndId) {
        return "{\"event_id\":\"" + eventId + "\",\"event_type\":\"pix.received\",\"end_to_end_id\":\""
                + endToEndId + "\",\"amount\":250.00}";
    }

    @Test
    void recebimentoValidoCriaEventoProcessadoERecebimento() {
        String eventId = "evt-" + UUID.randomUUID();
        String e2e = "E2E-" + UUID.randomUUID();
        String payload = recebimentoPayload(eventId, e2e);

        RestAssured.given()
                .header("X-Webhook-Signature", computeHmac(payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(URL)
                .then()
                .statusCode(202);

        PixWebhookEvent evento =
                webhookEventRepository.findByProviderAndEventId("celcoin-pix", eventId).orElseThrow();
        assertThat(evento.getStatus()).isEqualTo(StatusPixWebhookEvent.PROCESSADO);
        assertThat(recebimentoRepository.findByEndToEndId(e2e)).isPresent();
    }

    @Test
    void payloadSensivelNaoEhPersistidoEmClaro() {
        String eventId = "evt-" + UUID.randomUUID();
        String e2e = "E2E-" + UUID.randomUUID();
        String payload = recebimentoPayload(eventId, e2e);

        RestAssured.given()
                .header("X-Webhook-Signature", computeHmac(payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(URL)
                .then()
                .statusCode(202);

        PixWebhookEvent evento =
                webhookEventRepository.findByProviderAndEventId("celcoin-pix", eventId).orElseThrow();
        // Apenas o hash SHA-256 e guardado — nunca o payload bruto.
        assertThat(evento.getPayloadHash()).matches("[a-f0-9]{64}").isNotEqualTo(payload);
    }

    @Test
    void eventoDuplicadoNaoReprocessa() {
        String eventId = "evt-" + UUID.randomUUID();
        String e2e = "E2E-" + UUID.randomUUID();
        String payload = recebimentoPayload(eventId, e2e);

        for (int i = 0; i < 2; i++) {
            RestAssured.given()
                    .header("X-Webhook-Signature", computeHmac(payload))
                    .contentType(ContentType.JSON)
                    .body(payload)
                    .when()
                    .post(URL)
                    .then()
                    .statusCode(202);
        }

        assertThat(recebimentoRepository.findByEndToEndId(e2e)).isPresent();
        // Idempotencia: um unico recebimento para o mesmo end-to-end id.
        assertThat(recebimentoRepository.findAll().stream()
                        .filter(r -> e2e.equals(r.getEndToEndId()))
                        .count())
                .isEqualTo(1);
    }

    @Test
    void assinaturaInvalidaRetorna401() {
        String payload = recebimentoPayload("evt-" + UUID.randomUUID(), "E2E-" + UUID.randomUUID());
        RestAssured.given()
                .header("X-Webhook-Signature", "deadbeef")
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(URL)
                .then()
                .statusCode(401);
    }

    @Test
    void headerAssinaturaAusenteRetorna400() {
        String payload = recebimentoPayload("evt-" + UUID.randomUUID(), "E2E-" + UUID.randomUUID());
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(URL)
                .then()
                .statusCode(400);
    }

    @Test
    void aceitaAliasXCelcoinSignature() {
        String eventId = "evt-" + UUID.randomUUID();
        String e2e = "E2E-" + UUID.randomUUID();
        String payload = recebimentoPayload(eventId, e2e);

        RestAssured.given()
                .header("X-Celcoin-Signature", computeHmac(payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(URL)
                .then()
                .statusCode(202);

        assertThat(webhookEventRepository.findByProviderAndEventId("celcoin-pix", eventId).orElseThrow().getStatus())
                .isEqualTo(StatusPixWebhookEvent.PROCESSADO);
    }

    @Test
    void recebimentoSemValorMarcaFalhouSemCriarRecebimento() {
        String eventId = "evt-" + UUID.randomUUID();
        String e2e = "E2E-" + UUID.randomUUID();
        // Recebimento sem "amount": dominio rejeita valor nulo -> evento FALHOU, sem recebimento.
        String payload = "{\"event_id\":\"" + eventId + "\",\"event_type\":\"pix.received\",\"end_to_end_id\":\""
                + e2e + "\"}";

        RestAssured.given()
                .header("X-Webhook-Signature", computeHmac(payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(URL)
                .then()
                .statusCode(202);

        assertThat(webhookEventRepository.findByProviderAndEventId("celcoin-pix", eventId).orElseThrow().getStatus())
                .isEqualTo(StatusPixWebhookEvent.FALHOU);
        assertThat(recebimentoRepository.findByEndToEndId(e2e)).isEmpty();
    }

    @Test
    void correlationIdDoHeaderEhPersistidoNoRecebimento() {
        String eventId = "evt-" + UUID.randomUUID();
        String e2e = "E2E-" + UUID.randomUUID();
        String correlationId = "corr-" + UUID.randomUUID();
        String payload = recebimentoPayload(eventId, e2e);

        RestAssured.given()
                .header("X-Webhook-Signature", computeHmac(payload))
                .header("X-Correlation-Id", correlationId)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(URL)
                .then()
                .statusCode(202);

        assertThat(recebimentoRepository.findByEndToEndId(e2e).orElseThrow().getCorrelationId())
                .isEqualTo(correlationId);
    }

    @Test
    void tipoDesconhecidoMarcaIgnorado() {
        String eventId = "evt-" + UUID.randomUUID();
        String payload = "{\"event_id\":\"" + eventId + "\",\"event_type\":\"foo.bar\"}";

        RestAssured.given()
                .header("X-Webhook-Signature", computeHmac(payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(URL)
                .then()
                .statusCode(202);

        PixWebhookEvent evento =
                webhookEventRepository.findByProviderAndEventId("celcoin-pix", eventId).orElseThrow();
        assertThat(evento.getStatus()).isEqualTo(StatusPixWebhookEvent.IGNORADO);
    }

    private static String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
