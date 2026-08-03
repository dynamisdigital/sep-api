package com.dynamis.sep_api.identity.web;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/v1/auth/politica-lockout} publico (Sprint 34 Task 34.5).
 *
 * <p>Os tres valores sao <b>deliberadamente diferentes dos defaults</b> (5/15/30). Com os defaults,
 * um controller que devolvesse constantes hardcoded passaria — e o ponto do endpoint e justamente
 * refletir a configuracao efetiva do ambiente, que vem de env var.
 *
 * <p>O rate limit sobe junto porque o {@code RateLimitLockoutValidator} derruba o boot se o limite
 * por IP nao for estritamente maior que {@code max-attempts} (Task 34.4) — com {@code maxAttempts=7}
 * o default de 10 ainda passaria, mas deixar explicito evita que um ajuste futuro no fixture quebre
 * o contexto por um motivo sem relacao com este teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PoliticaLockoutIT {

    private static final int MAX_ATTEMPTS = 7;
    private static final int WINDOW_MINUTES = 11;
    private static final int LOCKOUT_MINUTES = 13;

    @DynamicPropertySource
    static void configurarPolitica(DynamicPropertyRegistry registry) {
        registry.add("app.security.lockout.max-attempts", () -> MAX_ATTEMPTS);
        registry.add("app.security.lockout.window-minutes", () -> WINDOW_MINUTES);
        registry.add("app.security.lockout.lockout-minutes", () -> LOCKOUT_MINUTES);
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
        registry.add("app.security.rate-limit.totp-verify-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
    }

    @Test
    void respondeSemAutenticacaoRefletindoAConfiguracaoEfetiva() {
        Response resposta = RestAssured.given().when().get("/api/v1/auth/politica-lockout");

        assertThat(resposta.statusCode())
                .as("quem precisa da politica esta bloqueado e nao tem sessao para apresentar")
                .isEqualTo(200);
        assertThat(resposta.jsonPath().getInt("maxAttempts")).isEqualTo(MAX_ATTEMPTS);
        assertThat(resposta.jsonPath().getInt("windowMinutes")).isEqualTo(WINDOW_MINUTES);
        assertThat(resposta.jsonPath().getInt("lockoutMinutes")).isEqualTo(LOCKOUT_MINUTES);
    }

    /**
     * O {@code permitAll} e por metodo: abrir a leitura nao pode abrir escrita no mesmo path, senao
     * um {@code POST} futuro nasceria publico sem ninguem decidir isso.
     */
    @Test
    void naoAbreOutrosMetodosNoMesmoPath() {
        Response resposta = RestAssured.given().when().post("/api/v1/auth/politica-lockout");

        assertThat(resposta.statusCode()).isNotEqualTo(200);
    }
}
