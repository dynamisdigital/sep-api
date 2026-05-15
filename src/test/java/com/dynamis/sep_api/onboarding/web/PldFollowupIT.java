package com.dynamis.sep_api.onboarding.web;

import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.FakeBackgroundCheckProvider;
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
 * E2E backend do follow-up automatico PLD apos KYC PF (Task 7.9.2). KYC APROVADO via webhook
 * dispara {@code PldOrchestrationListener} -> {@code IniciarPldPessoaUseCase} sincronamente
 * (AFTER_COMMIT na mesma thread); status final ({@code APROVADO_FINAL} ou {@code REPROVADO_PLD})
 * fica consolidado antes do controller responder ao webhook.
 *
 * <p>Cenarios:
 * <ul>
 *   <li>KYC APROVADO + PLD limpo -> APROVADO_FINAL
 *   <li>KYC APROVADO + hit PLD -> REPROVADO_PLD
 *   <li>KYC REPROVADO -> PLD nao dispara
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class PldFollowupIT {

    private static final String CPF_FELIZ = "52998224725";
    private static final String CPF_HIT = "11144477735";
    private static final String CPF_REPROVADO = "39053344705";
    private static final String WEBHOOK_SECRET = "dev-kyc-webhook-secret-change-me";

    @DynamicPropertySource
    static void desligarRateLimitNoTeste(DynamicPropertyRegistry registry) {
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
        FakeBackgroundCheckProvider.limparHits();
    }

    @AfterEach
    void cleanup() {
        limpar();
        FakeBackgroundCheckProvider.limparHits();
    }

    private void limpar() {
        consultaPldRepository.deleteAll();
        documentoRepository.deleteAll();
        resultadoRepository.deleteAll();
        solicitacaoRepository.deleteAll();
        webhookEventLogRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    private record ClienteCriado(UUID id, String token) {}

    private ClienteCriado criarClienteELogar() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "pld-" + suffix + "@sep.test";
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
        return new ClienteCriado(UUID.fromString(id), token);
    }

    private String iniciarPf(String token, String cpf) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"cpf\":\"" + cpf + "\",\"nomeCompleto\":\"Joao\",\"dataNascimento\":\"1990-01-01\"}")
                .when()
                .post("/api/v1/onboarding/pessoa")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private void uploadDoc(String token, String solicitacaoId, String tipo) {
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .multiPart("arquivo", "doc.jpg", new byte[] {1, 2, 3}, "image/jpeg")
                .multiPart("tipo", tipo)
                .when()
                .post("/api/v1/onboarding/pessoa/" + solicitacaoId + "/documentos")
                .then()
                .statusCode(204);
    }

    private void uploadMinimoPf(String token, String solicitacaoId) {
        uploadDoc(token, solicitacaoId, "RG");
        uploadDoc(token, solicitacaoId, "SELFIE");
    }

    private void disparar(String token, String solicitacaoId) {
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/onboarding/pessoa/" + solicitacaoId + "/verificar")
                .then()
                .statusCode(202);
    }

    private void webhookKyc(String solicitacaoId, String resultado) {
        String payload = "{\"verification_id\":\"fake-" + solicitacaoId + "\",\"status\":\"" + resultado + "\"}";
        RestAssured.given()
                .header("Idempotency-Key", "idem-" + solicitacaoId + "-" + resultado)
                .header("X-Webhook-Signature", hmacHex(payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/webhooks/celcoin/kyc")
                .then()
                .statusCode(202);
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

    private Response consultar(String token, String solicitacaoId) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/onboarding/pessoa/" + solicitacaoId)
                .then()
                .statusCode(200)
                .extract()
                .response();
    }

    // ============================================================

    @Test
    void kycAprovadoComPldLimpoMovePraAprovadoFinal() {
        ClienteCriado cliente = criarClienteELogar();
        String solicitacaoId = iniciarPf(cliente.token(), CPF_FELIZ);
        uploadMinimoPf(cliente.token(), solicitacaoId);
        disparar(cliente.token(), solicitacaoId);

        webhookKyc(solicitacaoId, "APPROVED");

        Response status = consultar(cliente.token(), solicitacaoId);
        assertThat(status.path("status").toString()).isEqualTo("APROVADO_FINAL");
        UUID solicitacaoUuid = UUID.fromString(solicitacaoId);
        assertThat(consultaPldRepository.findBySolicitacaoId(solicitacaoUuid)).hasSize(4);
    }

    @Test
    void kycAprovadoComHitPldMovePraReprovadoPld() {
        FakeBackgroundCheckProvider.marcarDocumentoComoHit(CPF_HIT);

        ClienteCriado cliente = criarClienteELogar();
        String solicitacaoId = iniciarPf(cliente.token(), CPF_HIT);
        uploadMinimoPf(cliente.token(), solicitacaoId);
        disparar(cliente.token(), solicitacaoId);

        webhookKyc(solicitacaoId, "APPROVED");

        Response status = consultar(cliente.token(), solicitacaoId);
        assertThat(status.path("status").toString()).isEqualTo("REPROVADO_PLD");
    }

    @Test
    void kycReprovadoNaoDisparaPld() {
        ClienteCriado cliente = criarClienteELogar();
        String solicitacaoId = iniciarPf(cliente.token(), CPF_REPROVADO);
        uploadMinimoPf(cliente.token(), solicitacaoId);
        disparar(cliente.token(), solicitacaoId);

        webhookKyc(solicitacaoId, "REJECTED");

        Response status = consultar(cliente.token(), solicitacaoId);
        assertThat(status.path("status").toString()).isEqualTo("REPROVADO");
        UUID solicitacaoUuid = UUID.fromString(solicitacaoId);
        assertThat(consultaPldRepository.findBySolicitacaoId(solicitacaoUuid)).isEmpty();
    }
}
