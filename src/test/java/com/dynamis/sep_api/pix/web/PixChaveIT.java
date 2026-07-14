package com.dynamis.sep_api.pix.web;

import com.dynamis.sep_api.escrow.domain.model.ContaEscrow;
import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.escrow.infrastructure.persistence.ContaEscrowRepository;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.pix.application.service.ChavePixSeguranca;
import com.dynamis.sep_api.pix.domain.model.ChavePix;
import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.infrastructure.adapter.fake.FakePixProvider;
import com.dynamis.sep_api.pix.infrastructure.persistence.ChavePixRepository;
import com.dynamis.sep_api.shared.audit.AuditLogSeguranca;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

/**
 * E2E backend da gestao assistida de chaves Pix (Sprint 31 Task 31.7). Sobe Spring Boot completo +
 * Postgres local ({@code sep_test}) com seguranca real (JWT + @PreAuthorize + step-up estrito) e
 * provider fake default. Valida o fluxo {@code POST -> GET -> DELETE -> GET}, idempotencia por
 * Idempotency-Key, colisao por valor normalizado, falha de provider sem estado orfao, minimizacao
 * (tabela, JSON e audit log nunca contem a chave bruta) e auditoria unica por transicao.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PixChaveIT {

    private static final String SENHA = "senha-passphrase-segura";
    private static final String PATH = "/api/v1/pix/chaves";
    private static final String CHAVE_EMAIL = "financeiro-chave@sep.test";

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

    @Autowired
    ChavePixRepository chavePixRepository;

    @Autowired
    ContaEscrowRepository contaEscrowRepository;

    @Autowired
    AuditLogSegurancaRepository auditLogRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    StepUpTokenService stepUpTokenService;

    @Autowired
    FakePixProvider fakePixProvider;

    @Autowired
    Environment environment;

    private UUID contaEscrowId;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
        limpar();
        fakePixProvider.reset();
        contaEscrowId = contaEscrowRepository
                .saveAndFlush(ContaEscrow.criar("SEP-COBRANCA", StatusContaEscrow.ATIVA))
                .getId();
    }

    @AfterEach
    void cleanup() {
        fakePixProvider.reset();
        limpar();
    }

    private void limpar() {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.contains("sep_test")) {
            throw new IllegalStateException("PixChaveIT deve rodar apenas no banco sep_test; URL: " + url);
        }
        chavePixRepository.deleteAll();
        contaEscrowRepository.deleteAll();
        auditLogRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // ============== fixtures ==============

    private record Autenticado(UUID id, String token) {}

    private Autenticado criarELogar(Role role, boolean mfa) {
        String email =
                role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@sep.test";
        Usuario u = Usuario.criar(email, passwordEncoder.encode(SENHA), role);
        if (mfa) {
            u.marcarMfaHabilitado();
        }
        u = usuarioRepository.saveAndFlush(u);
        String token = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + SENHA + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        return new Autenticado(u.getId(), token);
    }

    private String stepUp(Autenticado autenticado) {
        return stepUpTokenService.emitir(autenticado.id()).token();
    }

    private String cadastrar(Autenticado operador, String idempotencyKey, String valor, int statusEsperado) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + operador.token())
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Step-Up-Token", stepUp(operador))
                .contentType(ContentType.JSON)
                .body("{\"tipo\":\"EMAIL\",\"valor\":\"" + valor + "\"}")
                .when()
                .post(PATH)
                .then()
                .statusCode(statusEsperado)
                .extract()
                .asString();
    }

    private long auditCount(TipoEventoSeguranca tipo) {
        return auditLogRepository.findAll().stream()
                .filter(a -> a.getTipo() == tipo)
                .count();
    }

    // ============== cenarios ==============

    @Test
    void fluxoCompleto_cadastraListaRemoveEListaInativa_semChaveBrutaEmLugarNenhum() {
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);

        // POST 201: resposta mascarada, sem campos internos.
        String chaveId = RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("Idempotency-Key", "pix:chave:e2e:1")
                .header("X-Step-Up-Token", stepUp(financeiro))
                .contentType(ContentType.JSON)
                .body("{\"tipo\":\"EMAIL\",\"valor\":\"" + CHAVE_EMAIL + "\"}")
                .when()
                .post(PATH)
                .then()
                .statusCode(201)
                .body("valorMascarado", not(equalTo(CHAVE_EMAIL)))
                .body("status", equalTo("ATIVA"))
                .body("$", not(hasKey("valorHash")))
                .body("$", not(hasKey("providerKeyId")))
                .body("$", not(hasKey("idempotencyKey")))
                .body("$", not(hasKey("novo")))
                .extract()
                .path("id");

        // GET sem step-up: lista mascarada.
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get(PATH)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(chaveId))
                .body("[0].status", equalTo("ATIVA"));

        // DELETE 204 (step-up novo — token e consumido no uso).
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", stepUp(financeiro))
                .when()
                .delete(PATH + "/" + chaveId)
                .then()
                .statusCode(204);

        // GET pos-remocao: historico INATIVA preservado (nunca apagado).
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get(PATH)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].status", equalTo("INATIVA"))
                .body("[0].valorMascarado", not(equalTo(CHAVE_EMAIL)));

        // Minimizacao fim a fim: tabela guarda hash+mascara, nunca o valor bruto.
        List<ChavePix> linhas = chavePixRepository.findAll();
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getValorHash()).isEqualTo(ChavePixSeguranca.hashHex(CHAVE_EMAIL));
        assertThat(linhas.get(0).getValorMascarado()).isNotEqualTo(CHAVE_EMAIL);
        assertThat(linhas.get(0).getContaEscrowId()).isEqualTo(contaEscrowId);

        // Auditoria: exatamente 1 CADASTRADA + 1 REMOVIDA, detalhes sem dado sensivel.
        assertThat(auditCount(TipoEventoSeguranca.PIX_CHAVE_CADASTRADA)).isEqualTo(1);
        assertThat(auditCount(TipoEventoSeguranca.PIX_CHAVE_REMOVIDA)).isEqualTo(1);
        for (AuditLogSeguranca audit : auditLogRepository.findAll()) {
            assertThat(audit.getDetalhes()).doesNotContain(CHAVE_EMAIL);
            assertThat(audit.getDetalhes()).doesNotContain(ChavePixSeguranca.hashHex(CHAVE_EMAIL));
        }
    }

    @Test
    void replayDoPost_naoDuplicaLinhaNemAuditoria() {
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);

        String primeira = cadastrar(financeiro, "pix:chave:replay", CHAVE_EMAIL, 201);
        String replay = cadastrar(financeiro, "pix:chave:replay", CHAVE_EMAIL, 200);

        assertThat(chavePixRepository.findAll()).hasSize(1);
        assertThat(auditCount(TipoEventoSeguranca.PIX_CHAVE_CADASTRADA)).isEqualTo(1);
        assertThat(replay).contains(extrairId(primeira));
    }

    @Test
    void chaveEquivalenteAposNormalizacao_colide409() {
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);

        cadastrar(financeiro, "pix:chave:norm:1", CHAVE_EMAIL, 201);
        // Mesmo email com caixa/espacos diferentes normaliza igual -> conflito de chave ativa.
        String erro = cadastrar(financeiro, "pix:chave:norm:2", "  " + CHAVE_EMAIL.toUpperCase() + " ", 409);

        assertThat(erro).doesNotContain(CHAVE_EMAIL.toUpperCase());
        assertThat(chavePixRepository.findAll()).hasSize(1);
    }

    @Test
    void falhaDoProviderNoCadastro_naoDeixaChaveAtivaNemAuditoria() {
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        fakePixProvider.armarFalhaCadastroChave();

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("Idempotency-Key", "pix:chave:falha:1")
                .header("X-Step-Up-Token", stepUp(financeiro))
                .contentType(ContentType.JSON)
                .body("{\"tipo\":\"EMAIL\",\"valor\":\"" + CHAVE_EMAIL + "\"}")
                .when()
                .post(PATH)
                .then()
                .statusCode(502); // PixProviderException -> BAD_GATEWAY (handler vigente)

        assertThat(chavePixRepository.findAll()).isEmpty();
        assertThat(auditCount(TipoEventoSeguranca.PIX_CHAVE_CADASTRADA)).isZero();
    }

    @Test
    void falhaDoProviderNaRemocao_preservaChaveAtivaSemAuditoria() {
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        String chaveId = extrairId(cadastrar(financeiro, "pix:chave:falha:2", CHAVE_EMAIL, 201));
        fakePixProvider.armarFalhaRemocaoChave();

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", stepUp(financeiro))
                .when()
                .delete(PATH + "/" + chaveId)
                .then()
                .statusCode(502); // PixProviderException -> BAD_GATEWAY (handler vigente)

        assertThat(chavePixRepository
                        .findById(UUID.fromString(chaveId))
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StatusChavePix.ATIVA);
        assertThat(auditCount(TipoEventoSeguranca.PIX_CHAVE_REMOVIDA)).isZero();
    }

    @Test
    void deleteReplayEUuidInexistente_idempotenteE404Neutro() {
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        String chaveId = extrairId(cadastrar(financeiro, "pix:chave:del:1", CHAVE_EMAIL, 201));

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", stepUp(financeiro))
                .when()
                .delete(PATH + "/" + chaveId)
                .then()
                .statusCode(204);

        // Replay: chave ja INATIVA -> 204 sem nova auditoria.
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", stepUp(financeiro))
                .when()
                .delete(PATH + "/" + chaveId)
                .then()
                .statusCode(204);
        assertThat(auditCount(TipoEventoSeguranca.PIX_CHAVE_REMOVIDA)).isEqualTo(1);

        // UUID inexistente -> 404 com mensagem neutra.
        UUID inexistente = UUID.randomUUID();
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", stepUp(financeiro))
                .when()
                .delete(PATH + "/" + inexistente)
                .then()
                .statusCode(404)
                .body("message", not(equalTo(inexistente.toString())));
    }

    @Test
    void segurancaReal_401SemToken403SemRoleOuStepUp() {
        // Sem autenticacao -> 401 nos tres endpoints.
        RestAssured.given().when().get(PATH).then().statusCode(401);
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"tipo\":\"EMAIL\",\"valor\":\"x@y.com\"}")
                .when()
                .post(PATH)
                .then()
                .statusCode(401);
        RestAssured.given().when().delete(PATH + "/" + UUID.randomUUID()).then().statusCode(401);

        // CLIENTE -> 403.
        Autenticado cliente = criarELogar(Role.CLIENTE, true);
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get(PATH)
                .then()
                .statusCode(403);

        // FINANCEIRO sem step-up -> 403 na mutacao; GET funciona.
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("Idempotency-Key", "pix:chave:sec:1")
                .contentType(ContentType.JSON)
                .body("{\"tipo\":\"EMAIL\",\"valor\":\"" + CHAVE_EMAIL + "\"}")
                .when()
                .post(PATH)
                .then()
                .statusCode(403);
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get(PATH)
                .then()
                .statusCode(200);

        assertThat(chavePixRepository.findAll()).isEmpty();
    }

    @Test
    void postSemIdempotencyKey_400() {
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", stepUp(financeiro))
                .contentType(ContentType.JSON)
                .body("{\"tipo\":\"EMAIL\",\"valor\":\"" + CHAVE_EMAIL + "\"}")
                .when()
                .post(PATH)
                .then()
                .statusCode(400);
    }

    private static String extrairId(String json) {
        return io.restassured.path.json.JsonPath.from(json).getString("id");
    }
}
