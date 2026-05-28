package com.dynamis.sep_api.credores.web;

import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.PerfilCredoraRepository;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E backend da jornada credora foundation (Sprint 16 Task 16.6). Sobe Spring Boot completo em
 * RANDOM_PORT + Postgres local (profile {@code test}).
 *
 * <p>Cobre: cadastro feliz a partir de onboarding PJ APROVADO_FINAL -> credora ATIVA/ELEGIVEL +
 * auditoria; onboarding PJ REPROVADO -> INELEGIVEL; rejeicoes 404/403/409/422; consulta propria,
 * elegibilidade e consulta administrativa (ADMIN vs nao-ADMIN).
 *
 * <p>Onboarding aprovado eh montado via repositorio (atalho — evita fluxo KYB completo).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EmpresaCredoraIT {

    private static final String CNPJ_VALIDO = "11222333000181";

    @DynamicPropertySource
    static void configurarTest(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

    @Autowired
    SolicitacaoOnboardingRepository solicitacaoRepository;

    @Autowired
    KybEmpresaRepository kybRepository;

    @Autowired
    EmpresaCredoraRepository empresaRepository;

    @Autowired
    PerfilCredoraRepository perfilRepository;

    @Autowired
    AuditLogSegurancaRepository auditLogRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    Environment environment;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
        limpar();
    }

    @AfterEach
    void cleanup() {
        limpar();
    }

    private void limpar() {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.contains("sep_test")) {
            throw new IllegalStateException("EmpresaCredoraIT deve rodar apenas no banco sep_test; URL atual: " + url);
        }
        perfilRepository.deleteAll();
        empresaRepository.deleteAll();
        kybRepository.deleteAll();
        solicitacaoRepository.deleteAll();
        auditLogRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // ============== Fixtures ==============

    private record Autenticado(UUID id, String email, String token) {}

    private Autenticado criarClienteELogar() {
        return criarELogar(Role.CLIENTE);
    }

    private Autenticado criarAdminELogar() {
        return criarELogar(Role.ADMIN);
    }

    private Autenticado criarELogar(Role role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = role.name().toLowerCase() + "-" + suffix + "@sep.test";
        String senha = "senha-passphrase-segura";

        Usuario u;
        if (role == Role.CLIENTE) {
            String id = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body("{\"username\":\"" + email + "\",\"password\":\"" + senha + "\"}")
                    .when()
                    .post("/api/v1/usuarios")
                    .then()
                    .statusCode(201)
                    .extract()
                    .path("id");
            u = usuarioRepository.findById(UUID.fromString(id)).orElseThrow();
        } else {
            u = usuarioRepository.saveAndFlush(Usuario.criar(email, passwordEncoder.encode(senha), role));
        }

        String token = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + senha + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");

        return new Autenticado(u.getId(), email, token);
    }

    private UUID criarOnboardingEmpresa(UUID usuarioId, String cnpj, StatusOnboarding statusFinal) {
        SolicitacaoOnboarding s = SolicitacaoOnboarding.criarEmpresa(usuarioId, cnpj, "Credora Teste LTDA");
        s.registrarDocumentoEnviado();
        s.marcarEmVerificacao("fake-ext-" + UUID.randomUUID());
        if (statusFinal == StatusOnboarding.APROVADO_FINAL) {
            s.finalizar(StatusOnboarding.APROVADO);
            s.marcarAprovadoFinal();
        } else {
            s.finalizar(statusFinal);
        }
        UUID solicitacaoId = solicitacaoRepository.saveAndFlush(s).getId();

        KybEmpresa kyb = KybEmpresa.criar(
                solicitacaoId, new Cnpj(cnpj), "Credora Teste LTDA", null, TipoSocietario.LTDA, PorteEmpresa.EPP);
        kybRepository.saveAndFlush(kyb);
        return solicitacaoId;
    }

    private UUID criarOnboardingPessoa(UUID usuarioId) {
        SolicitacaoOnboarding s = SolicitacaoOnboarding.criarPessoa(
                usuarioId, new Cpf("52998224725"), "Pessoa Teste", LocalDate.of(1990, 1, 1));
        s.registrarDocumentoEnviado();
        s.marcarEmVerificacao("fake-ext-" + UUID.randomUUID());
        s.finalizar(StatusOnboarding.APROVADO);
        s.marcarAprovadoFinal();
        return solicitacaoRepository.saveAndFlush(s).getId();
    }

    private static void pollUntilAsserted(Runnable assercao) {
        long deadline = System.currentTimeMillis() + 5_000L;
        AssertionError ultimo = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                assercao.run();
                return;
            } catch (AssertionError ex) {
                ultimo = ex;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }
        throw ultimo != null ? ultimo : new AssertionError("Timeout sem assercao executada");
    }

    private String cadastrarBody(UUID onboardingId) {
        return "{\"onboardingId\":\"" + onboardingId + "\",\"tipoCredora\":\"EMPRESA\",\"capacidadeAporte\":100000.00}";
    }

    // ============== Fluxo feliz ==============

    @Test
    void cadastraCredoraAPartirDeOnboardingAprovadoFinalAtivaElegivelEAudita() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingEmpresa(cliente.id(), CNPJ_VALIDO, StatusOnboarding.APROVADO_FINAL);

        String credoraId = RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body(cadastrarBody(onbId))
                .when()
                .post("/api/v1/credores")
                .then()
                .statusCode(201)
                .body("status", org.hamcrest.Matchers.equalTo("ATIVA"))
                .body("elegibilidade", org.hamcrest.Matchers.equalTo("ELEGIVEL"))
                .body("cnpj", org.hamcrest.Matchers.equalTo("11.222.333/0001-81"))
                .extract()
                .path("id");

        assertThat(empresaRepository.findById(UUID.fromString(credoraId))).isPresent();
        pollUntilAsserted(() -> {
            assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                            cliente.id(), TipoEventoSeguranca.CREDORA_CADASTRADA))
                    .hasSize(1);
            assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                            cliente.id(), TipoEventoSeguranca.CREDORA_ELEGIVEL))
                    .hasSize(1);
        });
    }

    @Test
    void cadastraCredoraComOnboardingReprovadoFicaInelegivel() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingEmpresa(cliente.id(), CNPJ_VALIDO, StatusOnboarding.REPROVADO);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body(cadastrarBody(onbId))
                .when()
                .post("/api/v1/credores")
                .then()
                .statusCode(201)
                .body("status", org.hamcrest.Matchers.equalTo("CADASTRADA"))
                .body("elegibilidade", org.hamcrest.Matchers.equalTo("INELEGIVEL"));

        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.CREDORA_INELEGIVEL))
                .hasSize(1));
    }

    // ============== Negativos ==============

    @Test
    void onboardingInexistenteRetorna404() {
        Autenticado cliente = criarClienteELogar();
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body(cadastrarBody(UUID.randomUUID()))
                .when()
                .post("/api/v1/credores")
                .then()
                .statusCode(404);
    }

    @Test
    void onboardingPessoaFisicaRetorna422() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingPessoa(cliente.id());
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body(cadastrarBody(onbId))
                .when()
                .post("/api/v1/credores")
                .then()
                .statusCode(422);
    }

    @Test
    void onboardingDeOutroUsuarioRetorna403() {
        Autenticado dono = criarClienteELogar();
        Autenticado intruso = criarClienteELogar();
        UUID onbId = criarOnboardingEmpresa(dono.id(), CNPJ_VALIDO, StatusOnboarding.APROVADO_FINAL);

        RestAssured.given()
                .header("Authorization", "Bearer " + intruso.token())
                .contentType(ContentType.JSON)
                .body(cadastrarBody(onbId))
                .when()
                .post("/api/v1/credores")
                .then()
                .statusCode(403);
    }

    @Test
    void cadastroDuplicadoRetorna409() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingEmpresa(cliente.id(), CNPJ_VALIDO, StatusOnboarding.APROVADO_FINAL);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body(cadastrarBody(onbId))
                .when()
                .post("/api/v1/credores")
                .then()
                .statusCode(201);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body(cadastrarBody(onbId))
                .when()
                .post("/api/v1/credores")
                .then()
                .statusCode(409);
    }

    // ============== Consultas ==============

    @Test
    void consultaPropriaEElegibilidadeRetornamCredoraDoUsuario() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingEmpresa(cliente.id(), CNPJ_VALIDO, StatusOnboarding.APROVADO_FINAL);
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body(cadastrarBody(onbId))
                .when()
                .post("/api/v1/credores")
                .then()
                .statusCode(201);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/credores/me")
                .then()
                .statusCode(200)
                .body("usuarioId", org.hamcrest.Matchers.equalTo(cliente.id().toString()));

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/credores/me/elegibilidade")
                .then()
                .statusCode(200)
                .body("elegibilidade", org.hamcrest.Matchers.equalTo("ELEGIVEL"));
    }

    @Test
    void consultaPropriaSemCredoraRetorna404() {
        Autenticado cliente = criarClienteELogar();
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/credores/me")
                .then()
                .statusCode(404);
    }

    @Test
    void adminConsultaQualquerCredoraPorId() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingEmpresa(cliente.id(), CNPJ_VALIDO, StatusOnboarding.APROVADO_FINAL);
        String credoraId = RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body(cadastrarBody(onbId))
                .when()
                .post("/api/v1/credores")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        Autenticado admin = criarAdminELogar();
        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .get("/api/v1/credores/" + credoraId)
                .then()
                .statusCode(200)
                .body("id", org.hamcrest.Matchers.equalTo(credoraId));
    }

    @Test
    void consultaPorIdComoNaoAdminRetorna403() {
        Autenticado cliente = criarClienteELogar();
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/credores/" + UUID.randomUUID())
                .then()
                .statusCode(403);
    }
}
