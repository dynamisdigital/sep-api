package com.dynamis.sep_api.onboarding.web;

import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;
import com.dynamis.sep_api.onboarding.domain.vo.SituacaoCadastral;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.FakeBackgroundCheckProvider;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.FakeKybProvider;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaCNPJRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaPldRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.DocumentoCadastralRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.RepresentanteLegalRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E backend do fluxo KYB PJ + PLD orquestrado. Sobe contexto Spring Boot completo + Postgres
 * local (profile {@code dev}). {@link FakeKybProvider} e {@link FakeBackgroundCheckProvider} sao os
 * adapters default — usamos os helpers estaticos pra simular SUSPENSA e hit PLD em representante.
 *
 * <p>Cenarios cobertos (spec Task 7.9.1):
 * <ul>
 *   <li>Feliz: KYB ATIVA + PLD limpo -> APROVADO_FINAL
 *   <li>Hit PLD em representante -> REPROVADO_PLD
 *   <li>KYB SUSPENSA -> REPROVADO + PLD nao dispara
 *   <li>CNPJ duplicado em solicitacao ativa -> 409
 *   <li>Cliente B acessa solicitacao do A -> 403
 *   <li>Token ausente -> 401
 * </ul>
 *
 * <p>KYB e sincrono (provider sync); PLD dispara via {@code PldOrchestrationListener} AFTER_COMMIT
 * antes do controller responder 202, entao o estado consolidado e legivel imediatamente no GET.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class OnboardingEmpresaIT {

    private static final String CNPJ_FELIZ = "11222333000181";
    private static final String CNPJ_HIT_REP = "11444777000161";
    private static final String CNPJ_SUSPENSA = "33000167000101";
    private static final String CNPJ_DUPLICADO = "27865757000102";
    // CPF do representante fake (vem fixo do FakeKybProvider).
    private static final String CPF_REPRESENTANTE_FAKE = "52998224725";

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
    KybEmpresaRepository kybRepository;

    @Autowired
    ConsultaCNPJRepository consultaCnpjRepository;

    @Autowired
    RepresentanteLegalRepository representanteRepository;

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
        FakeKybProvider.limparEstado();
        FakeBackgroundCheckProvider.limparHits();
    }

    @AfterEach
    void cleanup() {
        limpar();
        FakeKybProvider.limparEstado();
        FakeBackgroundCheckProvider.limparHits();
    }

    private void limpar() {
        consultaPldRepository.deleteAll();
        representanteRepository.deleteAll();
        consultaCnpjRepository.deleteAll();
        kybRepository.deleteAll();
        documentoRepository.deleteAll();
        resultadoRepository.deleteAll();
        solicitacaoRepository.deleteAll();
        webhookEventLogRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    private record ClienteCriado(UUID id, String email, String token) {}

    private ClienteCriado criarClienteELogar() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "pj-" + suffix + "@sep.test";
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

    private String iniciarOnboardingEmpresa(String token, String cnpj) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"cnpj\":\"" + cnpj
                        + "\",\"razaoSocial\":\"ACME LTDA\",\"tipoSocietario\":\"LTDA\",\"porte\":\"ME\"}")
                .when()
                .post("/api/v1/onboarding/empresa")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private void uploadDocumento(String token, String solicitacaoId, String tipo, byte[] bytes) {
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .multiPart("arquivo", "doc.pdf", bytes, "application/pdf")
                .multiPart("tipo", tipo)
                .when()
                .post("/api/v1/onboarding/empresa/" + solicitacaoId + "/documentos")
                .then()
                .statusCode(204);
    }

    private void uploadDocumentosMinimosPj(String token, String solicitacaoId) {
        uploadDocumento(token, solicitacaoId, "CONTRATO_SOCIAL", new byte[] {1, 2, 3});
        uploadDocumento(token, solicitacaoId, "COMPROVANTE_ENDERECO", new byte[] {4, 5, 6});
    }

    // ============== Cenario feliz ==============

    @Test
    void fluxoCompletoCriaUploadVerificarPldLimpoChegaAprovadoFinal() {
        ClienteCriado cliente = criarClienteELogar();
        String solicitacaoId = iniciarOnboardingEmpresa(cliente.token(), CNPJ_FELIZ);
        uploadDocumentosMinimosPj(cliente.token(), solicitacaoId);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/onboarding/empresa/" + solicitacaoId + "/verificar")
                .then()
                .statusCode(202);

        Response status = RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/onboarding/empresa/" + solicitacaoId)
                .then()
                .statusCode(200)
                .extract()
                .response();
        assertThat(status.path("status").toString()).isEqualTo("APROVADO_FINAL");
        assertThat(status.path("dadosEmpresa.cnpj").toString()).isEqualTo("11.222.333/0001-81");
        assertThat(status.path("representantes[0].cpfMascarado").toString()).isEqualTo("529******25");
        assertThat(status.path("representantes[0].pld.statusPld").toString()).isEqualTo("LIMPO");

        // PLD persiste 4 bases obrigatorias por alvo: 4 (EMPRESA) + 4 (REPRESENTANTE) = 8 limpas.
        UUID solicitacaoUuid = UUID.fromString(solicitacaoId);
        var consultas = consultaPldRepository.findBySolicitacaoId(solicitacaoUuid);
        assertThat(consultas).hasSize(8).allSatisfy(c -> assertThat(c.isHit()).isFalse());
        assertThat(consultas.stream()
                        .filter(c -> c.getAlvoTipo() == AlvoPld.EMPRESA)
                        .count())
                .isEqualTo(4);
        assertThat(consultas.stream()
                        .filter(c -> c.getAlvoTipo() == AlvoPld.REPRESENTANTE)
                        .count())
                .isEqualTo(4);
        // Sanity: nenhuma consulta PLD ficou marcada como PESSOA (cenario PJ).
        assertThat(consultas).noneMatch(c -> c.getAlvoTipo() == AlvoPld.PESSOA);
    }

    // ============== Hit PLD em representante ==============

    @Test
    void hitPldEmRepresentanteReprovaOnboarding() {
        FakeBackgroundCheckProvider.marcarDocumentoComoHit(CPF_REPRESENTANTE_FAKE);

        ClienteCriado cliente = criarClienteELogar();
        String solicitacaoId = iniciarOnboardingEmpresa(cliente.token(), CNPJ_HIT_REP);
        uploadDocumentosMinimosPj(cliente.token(), solicitacaoId);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/onboarding/empresa/" + solicitacaoId + "/verificar")
                .then()
                .statusCode(202);

        Response status = RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/onboarding/empresa/" + solicitacaoId)
                .then()
                .statusCode(200)
                .extract()
                .response();
        assertThat(status.path("status").toString()).isEqualTo("REPROVADO_PLD");
        assertThat(status.path("representantes[0].pld.statusPld").toString()).isEqualTo("HIT");
    }

    // ============== KYB SUSPENSA ==============

    @Test
    void kybSuspensaReprovaEPldNaoDispara() {
        FakeKybProvider.marcarCnpjComoSituacao(CNPJ_SUSPENSA, SituacaoCadastral.SUSPENSA);

        ClienteCriado cliente = criarClienteELogar();
        String solicitacaoId = iniciarOnboardingEmpresa(cliente.token(), CNPJ_SUSPENSA);
        uploadDocumentosMinimosPj(cliente.token(), solicitacaoId);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/onboarding/empresa/" + solicitacaoId + "/verificar")
                .then()
                .statusCode(202);

        Response status = RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/onboarding/empresa/" + solicitacaoId)
                .then()
                .statusCode(200)
                .extract()
                .response();
        assertThat(status.path("status").toString()).isEqualTo("REPROVADO");
        // PLD nao dispara: nao ha consultas PLD persistidas pra solicitacao
        UUID solicitacaoUuid = UUID.fromString(solicitacaoId);
        assertThat(consultaPldRepository.findBySolicitacaoId(solicitacaoUuid)).isEmpty();
    }

    // ============== Negativos ==============

    @Test
    void cnpjDuplicadoEmSolicitacaoAtivaRetorna409() {
        ClienteCriado cliente = criarClienteELogar();
        iniciarOnboardingEmpresa(cliente.token(), CNPJ_DUPLICADO);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body("{\"cnpj\":\"" + CNPJ_DUPLICADO + "\",\"razaoSocial\":\"OUTRA LTDA\"}")
                .when()
                .post("/api/v1/onboarding/empresa")
                .then()
                .statusCode(409);
    }

    @Test
    void clienteBAcessaSolicitacaoDoAResulta403() {
        ClienteCriado clienteA = criarClienteELogar();
        ClienteCriado clienteB = criarClienteELogar();
        String solicitacaoA = iniciarOnboardingEmpresa(clienteA.token(), CNPJ_FELIZ);

        RestAssured.given()
                .header("Authorization", "Bearer " + clienteB.token())
                .when()
                .get("/api/v1/onboarding/empresa/" + solicitacaoA)
                .then()
                .statusCode(403);
    }

    @Test
    void tokenAusenteRetorna401() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"cnpj\":\"" + CNPJ_FELIZ + "\",\"razaoSocial\":\"X\"}")
                .when()
                .post("/api/v1/onboarding/empresa")
                .then()
                .statusCode(401);
    }
}
