package com.dynamis.sep_api;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E ponta a ponta da API SEP. Sobe o contexto Spring Boot completo em {@code RANDOM_PORT}
 * apontando para o PostgreSQL local (Docker Compose) via profile {@code dev}.
 *
 * <p>Erratum: spec 004 originalmente pede Testcontainers; o projeto mantem Postgres local por
 * incompatibilidade conhecida do docker-java em TC 1.21 com Docker Engine 28+ (mesma decisao da
 * Sprint 1). Migracao para Testcontainers ficou como follow-up cross-sprint.
 *
 * <p>5F-FIX-01: cadastro publico nao cria mais ADMIN. ADMIN do smoke e seedado direto no
 * repositorio (mesmo papel de seed/migration documentado no plano de follow-up).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class SmokeE2ETest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void fluxoCompletoCriacaoLoginConsultaSenhaAutorizacao() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "smoke-admin-" + suffix + "@sep.test";
        String clienteEmail = "smoke-cliente-" + suffix + "@sep.test";
        String adminSenha = "senha-passphrase-segura";
        String clienteSenhaInicial = "nova-passphrase-segura";
        String clienteSenhaNova = "outra-passphrase-segura";

        // 1. ADMIN seedado direto (cadastro publico nao cria ADMIN apos 5F-FIX-01)
        Usuario admin =
                usuarioRepository.save(Usuario.criar(adminEmail, passwordEncoder.encode(adminSenha), Role.ADMIN));
        String adminId = admin.getId().toString();

        // 2. Cliente via cadastro publico (sempre CLIENTE)
        String clienteId = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + clienteEmail + "\",\"password\":\"" + clienteSenhaInicial + "\"}")
                .when()
                .post("/api/v1/usuarios")
                .then()
                .statusCode(201)
                .body("role", org.hamcrest.Matchers.equalTo("CLIENTE"))
                .extract()
                .path("id");

        // 3. Login admin
        Response loginAdmin = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + adminEmail + "\",\"password\":\"" + adminSenha + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .response();
        String adminToken = loginAdmin.path("accessToken");
        assertThat(adminToken).isNotBlank();

        // 4. Login cliente
        String clienteToken = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + clienteEmail + "\",\"password\":\"" + clienteSenhaInicial + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        assertThat(clienteToken).isNotNull();

        // 5. /auth/me com admin
        RestAssured.given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/auth/me")
                .then()
                .statusCode(200)
                .body("username", org.hamcrest.Matchers.equalTo(adminEmail));

        // 6. GET /usuarios admin -> 200
        RestAssured.given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/usuarios")
                .then()
                .statusCode(200);

        // 7. GET /usuarios cliente -> 403
        RestAssured.given()
                .header("Authorization", "Bearer " + clienteToken)
                .when()
                .get("/api/v1/usuarios")
                .then()
                .statusCode(403);

        // 8. PATCH senha do proprio cliente -> 204
        RestAssured.given()
                .header("Authorization", "Bearer " + clienteToken)
                .contentType(ContentType.JSON)
                .body("{\"passwordAtual\":\"" + clienteSenhaInicial + "\",\"novaSenha\":\"" + clienteSenhaNova + "\"}")
                .when()
                .patch("/api/v1/usuarios/" + clienteId + "/senha")
                .then()
                .statusCode(204);

        // 9. PATCH senha do admin com cliente -> 403
        RestAssured.given()
                .header("Authorization", "Bearer " + clienteToken)
                .contentType(ContentType.JSON)
                .body("{\"passwordAtual\":\"" + clienteSenhaNova + "\",\"novaSenha\":\"mais-uma-passphrase\"}")
                .when()
                .patch("/api/v1/usuarios/" + adminId + "/senha")
                .then()
                .statusCode(403);

        // 10. 5F-FIX-01: payload publico com role=ADMIN nao escala — usuario sempre CLIENTE
        String tentativaEscalada = "smoke-attacker-" + suffix + "@sep.test";
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + tentativaEscalada
                        + "\",\"password\":\"nova-passphrase-attack\",\"role\":\"ADMIN\"}")
                .when()
                .post("/api/v1/usuarios")
                .then()
                .statusCode(201)
                .body("role", org.hamcrest.Matchers.equalTo("CLIENTE"));

        // 11. /admin/usuarios sem token -> 401
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"smoke-noauth-" + suffix
                        + "@sep.test\",\"password\":\"passphrase-segura-x\",\"role\":\"ADMIN\"}")
                .when()
                .post("/api/v1/admin/usuarios")
                .then()
                .statusCode(401);

        // 12. /admin/usuarios com CLIENTE -> 403
        RestAssured.given()
                .header("Authorization", "Bearer " + clienteToken)
                .contentType(ContentType.JSON)
                .body("{\"username\":\"smoke-cliente-tenta-" + suffix
                        + "@sep.test\",\"password\":\"passphrase-segura-x\",\"role\":\"ADMIN\"}")
                .when()
                .post("/api/v1/admin/usuarios")
                .then()
                .statusCode(403);

        // 13. /admin/usuarios com ADMIN -> 201 cria outro ADMIN
        String novoAdminEmail = "smoke-novo-admin-" + suffix + "@sep.test";
        RestAssured.given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + novoAdminEmail
                        + "\",\"password\":\"passphrase-novo-admin-segura\",\"role\":\"ADMIN\"}")
                .when()
                .post("/api/v1/admin/usuarios")
                .then()
                .statusCode(201)
                .body("role", org.hamcrest.Matchers.equalTo("ADMIN"));
    }

    @Test
    void usuarioComResetObrigatorioFicaConfinadoAteTrocarSenha() {
        // 5F-FIX-04: ao logar com precisaRedefinirSenha=true, o token emitido carrega claim
        // password_reset_required=true. O filtro server-side bloqueia rotas comuns ate a troca.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "smoke-reset-" + suffix + "@sep.test";
        String senhaInicial = "senha-inicial-segura";
        String senhaNova = "senha-nova-segura";

        Usuario usuario = Usuario.criar(email, passwordEncoder.encode(senhaInicial), Role.CLIENTE);
        // Seta flag precisaRedefinirSenha=true (estado pos-V6 ou simulando expiracao de senha)
        marcarRedefinicaoObrigatoria(usuario);
        usuario = usuarioRepository.save(usuario);
        String userId = usuario.getId().toString();

        String token = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + senhaInicial + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        assertThat(token).isNotBlank();

        // /auth/me liberado mesmo sob reset
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/auth/me")
                .then()
                .statusCode(200)
                .body("precisaRedefinirSenha", org.hamcrest.Matchers.equalTo(true));

        // GET /usuarios (rota comum) -> 403 com codigo
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/usuarios")
                .then()
                .statusCode(403)
                .body("message", org.hamcrest.Matchers.containsString("AUTH-403-PASSWORD_RESET_REQUIRED"));

        // PATCH proprio password -> 204 (e zera flag)
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"passwordAtual\":\"" + senhaInicial + "\",\"novaSenha\":\"" + senhaNova + "\"}")
                .when()
                .patch("/api/v1/usuarios/" + userId + "/senha")
                .then()
                .statusCode(204);

        // Novo login emite token sem o claim; rotas comuns liberadas (ate o ponto que ownership/role permite)
        String tokenLimpo = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + senhaNova + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");

        RestAssured.given()
                .header("Authorization", "Bearer " + tokenLimpo)
                .when()
                .get("/api/v1/auth/me")
                .then()
                .statusCode(200)
                .body("precisaRedefinirSenha", org.hamcrest.Matchers.equalTo(false));
    }

    private static void marcarRedefinicaoObrigatoria(Usuario usuario) {
        try {
            java.lang.reflect.Field f = Usuario.class.getDeclaredField("precisaRedefinirSenha");
            f.setAccessible(true);
            f.setBoolean(usuario, true);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Falha ao setar flag precisaRedefinirSenha via reflection", ex);
        }
    }
}
