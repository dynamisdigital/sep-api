package com.dynamis.sep_api.onboarding.web;

import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaPldRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.DocumentoCadastralRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ResultadoVerificacaoRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
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
 * E2E backend do fluxo KYC PF. Sobe o contexto Spring Boot completo em RANDOM_PORT + Postgres
 * local (profile {@code dev}). FakeKycProvider e o adapter default (app.kyc.provider=fake), entao
 * o webhook callback e simulado por POST direto neste teste.
 *
 * <p>Erratum vs spec 006: spec pede Testcontainers; mesmo desvio das Sprints 1-5 (Docker Engine
 * 28+ vs docker-java em TC 1.21). Migracao para TC fica como follow-up cross-sprint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class OnboardingPessoaIT {

    private static final String CPF_VALIDO_1 = "52998224725";
    private static final String CPF_VALIDO_2 = "11144477735";
    private static final String WEBHOOK_SECRET = "dev-kyc-webhook-secret-change-me";

    @DynamicPropertySource
    static void desligarRateLimitNoTeste(DynamicPropertyRegistry registry) {
        // O default dev (5 logins/min/IP) bate em 429 quando varios cenarios criam contas
        // novas no mesmo IP de loopback. Aumenta o cap apenas no escopo deste IT.
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

    @Autowired
    SolicitacaoOnboardingRepository solicitacaoRepository;

    @Autowired
    DocumentoCadastralRepository documentoRepository;

    @Autowired
    ResultadoVerificacaoRepository resultadoRepository;

    @Autowired
    ConsultaPldRepository consultaPldRepository;

    @Autowired
    WebhookEventLogRepository webhookEventLogRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
        limpar();
    }

    @AfterEach
    void cleanup() {
        // Sem CASCADE na FK (LGPD/regulatorio), limpamos filhas antes da tabela usuario para
        // nao quebrar testes legacy que rodam depois e fazem usuarioRepository.deleteAll().
        limpar();
    }

    private void limpar() {
        // consulta_pld FK sem CASCADE (LGPD retencao 5 anos) — limpar antes de solicitacao
        consultaPldRepository.deleteAll();
        documentoRepository.deleteAll();
        resultadoRepository.deleteAll();
        solicitacaoRepository.deleteAll();
        webhookEventLogRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    private record ClienteCriado(UUID id, String email, String token) {}

    private ClienteCriado criarClienteELogar() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "onb-" + suffix + "@sep.test";
        String senha = "senha-passphrase-segura";

        String id = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + senha + "\"}")
                .when()
                .post("/api/v1/usuarios")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String token = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + senha + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");

        return new ClienteCriado(UUID.fromString(id), email, token);
    }

    private String iniciarOnboarding(String token, String cpf) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"cpf\":\"" + cpf + "\",\"nomeCompleto\":\"Joao Teste\",\"dataNascimento\":\"1990-01-01\"}")
                .when()
                .post("/api/v1/onboarding/pessoa")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private void uploadDocumento(String token, String solicitacaoId, String tipo, byte[] bytes, String mime) {
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .multiPart("arquivo", "doc.jpg", bytes, mime)
                .multiPart("tipo", tipo)
                .when()
                .post("/api/v1/onboarding/pessoa/" + solicitacaoId + "/documentos")
                .then()
                .statusCode(204);
    }

    private static String hmacHex(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ============== Cenario feliz ==============

    @Test
    void fluxoCompletoCriarUploadVerificarCallbackAprovado() {
        ClienteCriado cliente = criarClienteELogar();
        String solicitacaoId = iniciarOnboarding(cliente.token(), CPF_VALIDO_1);

        uploadDocumento(cliente.token(), solicitacaoId, "RG", new byte[] {1, 2, 3}, "image/jpeg");
        uploadDocumento(cliente.token(), solicitacaoId, "SELFIE", new byte[] {4, 5, 6}, "image/jpeg");

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/onboarding/pessoa/" + solicitacaoId + "/verificar")
                .then()
                .statusCode(202);

        // Status apos disparo = EM_VERIFICACAO; idVerificacaoExterna = "fake-<id>"
        String idExterno = "fake-" + solicitacaoId;

        String payload = "{\"verification_id\":\"" + idExterno + "\",\"status\":\"APPROVED\"}";
        RestAssured.given()
                .header("Idempotency-Key", "idem-" + solicitacaoId)
                .header("X-Webhook-Signature", hmacHex(payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/webhooks/celcoin/kyc")
                .then()
                .statusCode(202);

        Response status = RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/onboarding/pessoa/" + solicitacaoId)
                .then()
                .statusCode(200)
                .extract()
                .response();
        assertThat(status.path("status").toString()).isEqualTo("APROVADO_FINAL");
        assertThat(status.path("resultado.statusFinal").toString()).isEqualTo("APROVADO");
    }

    // ============== Negativos ==============

    @Test
    void cpfDuplicadoEmSolicitacaoAtivaRetorna409() {
        ClienteCriado cliente = criarClienteELogar();
        iniciarOnboarding(cliente.token(), CPF_VALIDO_1);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body("{\"cpf\":\"" + CPF_VALIDO_1 + "\",\"nomeCompleto\":\"Outro\",\"dataNascimento\":\"1990-01-01\"}")
                .when()
                .post("/api/v1/onboarding/pessoa")
                .then()
                .statusCode(409);
    }

    @Test
    void arquivoMaiorQue10MBRetorna400() {
        ClienteCriado cliente = criarClienteELogar();
        String solicitacaoId = iniciarOnboarding(cliente.token(), CPF_VALIDO_1);
        byte[] grande = new byte[10 * 1024 * 1024 + 1]; // 10MB + 1 byte

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .multiPart("arquivo", "grande.jpg", grande, "image/jpeg")
                .multiPart("tipo", "RG")
                .when()
                .post("/api/v1/onboarding/pessoa/" + solicitacaoId + "/documentos")
                .then()
                .statusCode(400);
    }

    @Test
    void mimeInvalidoRetorna400() {
        ClienteCriado cliente = criarClienteELogar();
        String solicitacaoId = iniciarOnboarding(cliente.token(), CPF_VALIDO_1);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .multiPart("arquivo", "rg.bin", new byte[] {1, 2}, "application/octet-stream")
                .multiPart("tipo", "RG")
                .when()
                .post("/api/v1/onboarding/pessoa/" + solicitacaoId + "/documentos")
                .then()
                .statusCode(400);
    }

    @Test
    void verificarSemDocumentosMinimosRetorna400() {
        ClienteCriado cliente = criarClienteELogar();
        String solicitacaoId = iniciarOnboarding(cliente.token(), CPF_VALIDO_1);

        // Sem nenhum upload: 400 (StatusOnboardingInvalido — status=INICIADO nao permite disparar)
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/onboarding/pessoa/" + solicitacaoId + "/verificar")
                .then()
                .statusCode(400);

        // Com so RG (sem SELFIE) -> 400 (documentos minimos ausentes)
        uploadDocumento(cliente.token(), solicitacaoId, "RG", new byte[] {1}, "image/jpeg");
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/onboarding/pessoa/" + solicitacaoId + "/verificar")
                .then()
                .statusCode(400);
    }

    @Test
    void webhookAssinaturaInvalidaRetorna401() {
        String payload = "{\"verification_id\":\"fake-xyz\",\"status\":\"APPROVED\"}";
        RestAssured.given()
                .header("Idempotency-Key", "idem-bad")
                .header("X-Webhook-Signature", "deadbeef")
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/webhooks/celcoin/kyc")
                .then()
                .statusCode(401);
    }

    @Test
    void webhookIdempotenteDuplicadoNaoReprocessa() {
        ClienteCriado cliente = criarClienteELogar();
        String solicitacaoId = iniciarOnboarding(cliente.token(), CPF_VALIDO_1);
        uploadDocumento(cliente.token(), solicitacaoId, "RG", new byte[] {1}, "image/jpeg");
        uploadDocumento(cliente.token(), solicitacaoId, "SELFIE", new byte[] {2}, "image/jpeg");
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/onboarding/pessoa/" + solicitacaoId + "/verificar")
                .then()
                .statusCode(202);

        String payload = "{\"verification_id\":\"fake-" + solicitacaoId + "\",\"status\":\"APPROVED\"}";
        String idem = "idem-dup-" + solicitacaoId;

        // 1a chamada -> processa e finaliza
        RestAssured.given()
                .header("Idempotency-Key", idem)
                .header("X-Webhook-Signature", hmacHex(payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/webhooks/celcoin/kyc")
                .then()
                .statusCode(202);

        // 2a chamada (mesma key) -> 202 sem reprocessar
        RestAssured.given()
                .header("Idempotency-Key", idem)
                .header("X-Webhook-Signature", hmacHex(payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/webhooks/celcoin/kyc")
                .then()
                .statusCode(202);

        // Apenas 1 WebhookEventLog gravado com essa idempotencyKey
        assertThat(webhookEventLogRepository.existsByIdempotencyKey(idem)).isTrue();
        assertThat(webhookEventLogRepository.findAll().size()).isEqualTo(1);
    }

    @Test
    void clienteAnexaDocumentoEmSolicitacaoDeOutroCliente403() {
        ClienteCriado clienteA = criarClienteELogar();
        ClienteCriado clienteB = criarClienteELogar();
        String solicitacaoA = iniciarOnboarding(clienteA.token(), CPF_VALIDO_1);

        RestAssured.given()
                .header("Authorization", "Bearer " + clienteB.token())
                .multiPart("arquivo", "rg.jpg", new byte[] {1, 2, 3}, "image/jpeg")
                .multiPart("tipo", "RG")
                .when()
                .post("/api/v1/onboarding/pessoa/" + solicitacaoA + "/documentos")
                .then()
                .statusCode(403);
    }

    @Test
    void tokenAusenteRetorna401() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"cpf\":\"" + CPF_VALIDO_2 + "\",\"nomeCompleto\":\"X\",\"dataNascimento\":\"1990-01-01\"}")
                .when()
                .post("/api/v1/onboarding/pessoa")
                .then()
                .statusCode(401);
    }
}
