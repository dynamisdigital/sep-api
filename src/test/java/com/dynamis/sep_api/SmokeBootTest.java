package com.dynamis.sep_api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test que confirma:
 *
 * <ul>
 *   <li>contexto Spring carrega contra o Postgres local (Docker Compose, profile {@code dev})
 *   <li>{@code GET /actuator/health} responde 200 + status UP
 *   <li>Flyway aplica V1 e V2 no boot
 * </ul>
 *
 * <p>Pre-requisito de execucao: {@code docker compose up -d postgres} antes de rodar
 * {@code ./gradlew test}. Testcontainers sera adotado em sprint posterior quando o ambiente
 * docker-java estabilizar (issue conhecido com Docker Engine 28+ na versao atual do TC).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class SmokeBootTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void contextLoads() {
        // se o teste chega aqui, o contexto carregou OK (com migrations aplicadas)
        assertThat(port).isPositive();
    }

    @Test
    void healthCheckReturnsUp() {
        ResponseEntity<JsonNode> response =
                rest.getForEntity("http://localhost:" + port + "/actuator/health", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status").asText()).isEqualTo("UP");
    }
}
