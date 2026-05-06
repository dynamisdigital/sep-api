package com.dynamis.sep_api;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Smoke E2E ponta a ponta da API SEP. Sobe o contexto Spring Boot completo em {@code RANDOM_PORT}
 * apontando para o PostgreSQL local (Docker Compose) via profile {@code dev}.
 *
 * <p>Erratum: spec 004 originalmente pede Testcontainers; o projeto mantem Postgres local por
 * incompatibilidade conhecida do docker-java em TC 1.21 com Docker Engine 28+ (mesma decisao da
 * Sprint 1). Migracao para Testcontainers ficou como follow-up cross-sprint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class SmokeE2ETest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void fluxoCompletoCriacaoLoginConsultaSenhaAutorizacao() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "smoke-admin-" + suffix + "@sep.test";
        String clienteEmail = "smoke-cliente-" + suffix + "@sep.test";

        // 1. Cria admin
        String adminId = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + adminEmail + "\",\"password\":\"123456\",\"role\":\"ADMIN\"}")
                .when()
                .post("/api/v1/usuarios")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .extract()
                .path("id");

        // 2. Cria cliente
        String clienteId = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + clienteEmail + "\",\"password\":\"abcdef\",\"role\":\"CLIENTE\"}")
                .when()
                .post("/api/v1/usuarios")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // 3. Login admin
        Response loginAdmin = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + adminEmail + "\",\"password\":\"123456\"}")
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
                .body("{\"username\":\"" + clienteEmail + "\",\"password\":\"abcdef\"}")
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
                .body("{\"passwordAtual\":\"abcdef\",\"novaSenha\":\"zzzzzz\"}")
                .when()
                .patch("/api/v1/usuarios/" + clienteId + "/senha")
                .then()
                .statusCode(204);

        // 9. PATCH senha do admin com cliente -> 403
        RestAssured.given()
                .header("Authorization", "Bearer " + clienteToken)
                .contentType(ContentType.JSON)
                .body("{\"passwordAtual\":\"zzzzzz\",\"novaSenha\":\"yyyyyy\"}")
                .when()
                .patch("/api/v1/usuarios/" + adminId + "/senha")
                .then()
                .statusCode(403);
    }
}
