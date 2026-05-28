package com.dynamis.sep_api.governanca.web;

import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E de governanca (Sprint 18 Tasks 18.5/18.6): endpoints admin de roles cumulativas e de
 * parametros operacionais com step-up, mais auditoria. Step-up: admin mfa=false bypassa o aspect
 * (profile test); admin mfa=true sem X-Step-Up-Token -> 403; com token valido -> 200.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GovernancaRbacIT {

    @DynamicPropertySource
    static void cfg(DynamicPropertyRegistry r) {
        r.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    AuditLogSegurancaRepository auditLogRepository;

    @Autowired
    StepUpTokenService stepUpTokenService;

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
        if (!environment.getProperty("spring.datasource.url", "").contains("sep_test")) {
            throw new IllegalStateException("GovernancaRbacIT deve rodar apenas no banco sep_test");
        }
        auditLogRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    private static final String SENHA = "senha-passphrase-segura";

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
        throw ultimo != null ? ultimo : new AssertionError("timeout");
    }

    // ============== Roles cumulativas ==============

    @Test
    void adminSubstituiRolesComStepUpBypassEAudita() {
        Autenticado admin = criarELogar(Role.ADMIN, false);
        Autenticado alvo = criarELogar(Role.CLIENTE, false);

        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .contentType(ContentType.JSON)
                .body("{\"roles\":[\"FINANCEIRO\",\"BACKOFFICE\"]}")
                .when()
                .put("/api/v1/usuarios/" + alvo.id() + "/roles")
                .then()
                .statusCode(200)
                .body("roles", org.hamcrest.Matchers.containsInAnyOrder("FINANCEIRO", "BACKOFFICE"))
                .body("principal", org.hamcrest.Matchers.equalTo("FINANCEIRO"));

        // GET roles reflete
        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .get("/api/v1/usuarios/" + alvo.id() + "/roles")
                .then()
                .statusCode(200)
                .body("roles", org.hamcrest.Matchers.containsInAnyOrder("FINANCEIRO", "BACKOFFICE"));

        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        admin.id(), TipoEventoSeguranca.USUARIO_ROLES_ALTERADAS))
                .hasSize(1));
    }

    @Test
    void adminAdicionaERemoveRole() {
        Autenticado admin = criarELogar(Role.ADMIN, false);
        Autenticado alvo = criarELogar(Role.FINANCEIRO, false);

        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .post("/api/v1/usuarios/" + alvo.id() + "/roles/BACKOFFICE")
                .then()
                .statusCode(200)
                .body("roles", org.hamcrest.Matchers.containsInAnyOrder("FINANCEIRO", "BACKOFFICE"));

        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .delete("/api/v1/usuarios/" + alvo.id() + "/roles/FINANCEIRO")
                .then()
                .statusCode(200)
                .body("roles", org.hamcrest.Matchers.containsInAnyOrder("BACKOFFICE"));
    }

    @Test
    void removerUltimaRoleRetorna400() {
        Autenticado admin = criarELogar(Role.ADMIN, false);
        Autenticado alvo = criarELogar(Role.CLIENTE, false);

        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .delete("/api/v1/usuarios/" + alvo.id() + "/roles/CLIENTE")
                .then()
                .statusCode(400);
    }

    @Test
    void adminNaoAlteraProprioConjuntoDeRoles() {
        Autenticado admin = criarELogar(Role.ADMIN, false);
        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .contentType(ContentType.JSON)
                .body("{\"roles\":[\"CLIENTE\"]}")
                .when()
                .put("/api/v1/usuarios/" + admin.id() + "/roles")
                .then()
                .statusCode(403);
    }

    @Test
    void naoAdminNaoAlteraRoles() {
        Autenticado cliente = criarELogar(Role.CLIENTE, false);
        Autenticado alvo = criarELogar(Role.CLIENTE, false);
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/usuarios/" + alvo.id() + "/roles/FINANCEIRO")
                .then()
                .statusCode(403);
    }

    @Test
    void adminComMfaSemStepUpRetorna403NaAlteracaoDeRoles() {
        Autenticado admin = criarELogar(Role.ADMIN, true); // mfa -> step-up exigido
        Autenticado alvo = criarELogar(Role.CLIENTE, false);
        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .post("/api/v1/usuarios/" + alvo.id() + "/roles/FINANCEIRO")
                .then()
                .statusCode(403);
    }

    // ============== Parametros operacionais ==============

    @Test
    void adminListaConsultaEAlteraParametroComAuditoria() {
        Autenticado admin = criarELogar(Role.ADMIN, false);

        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .get("/api/v1/governanca/parametros")
                .then()
                .statusCode(200)
                .body("chave", org.hamcrest.Matchers.hasItem("credito.score.pre-aprovacao"));

        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .get("/api/v1/governanca/parametros/credito.score.pre-aprovacao")
                .then()
                .statusCode(200)
                .body("parametro.chave", org.hamcrest.Matchers.equalTo("credito.score.pre-aprovacao"));

        String valorOriginal = RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .get("/api/v1/governanca/parametros/credito.score.pre-aprovacao")
                .then()
                .extract()
                .path("parametro.valor");

        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .contentType(ContentType.JSON)
                .body("{\"novoValor\":\"800\",\"justificativa\":\"Ajuste politica de credito teste\"}")
                .when()
                .patch("/api/v1/governanca/parametros/credito.score.pre-aprovacao")
                .then()
                .statusCode(200)
                .body("valor", org.hamcrest.Matchers.equalTo("800"));

        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        admin.id(), TipoEventoSeguranca.PARAMETRO_OPERACIONAL_ALTERADO))
                .hasSize(1));

        // restaura valor original (banco sep_test compartilhado)
        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .contentType(ContentType.JSON)
                .body("{\"novoValor\":\"" + valorOriginal + "\",\"justificativa\":\"Restauracao pos-teste\"}")
                .when()
                .patch("/api/v1/governanca/parametros/credito.score.pre-aprovacao")
                .then()
                .statusCode(200);
    }

    @Test
    void alterarParametroComValorInvalidoRetorna400() {
        Autenticado admin = criarELogar(Role.ADMIN, false);
        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .contentType(ContentType.JSON)
                .body("{\"novoValor\":\"abc\",\"justificativa\":\"valor nao inteiro\"}")
                .when()
                .patch("/api/v1/governanca/parametros/credito.score.pre-aprovacao")
                .then()
                .statusCode(400);
    }

    @Test
    void naoAdminNaoAcessaGovernanca() {
        Autenticado cliente = criarELogar(Role.CLIENTE, false);
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/governanca/parametros")
                .then()
                .statusCode(403);
    }
}
