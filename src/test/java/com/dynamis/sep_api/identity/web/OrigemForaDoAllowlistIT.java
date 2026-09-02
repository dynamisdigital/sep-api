package com.dynamis.sep_api.identity.web;

import com.dynamis.sep_api.identity.domain.model.LoginAttempt;
import com.dynamis.sep_api.identity.infrastructure.persistence.LoginAttemptRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lado <b>de fora</b> do allowlist de proxy (Sprint 35 Task 35.2). Roda com o
 * {@code internal-proxies} que o {@code application.yml} entrega — vazio, isto e, nenhuma origem
 * confiavel —, entao o {@code X-Forwarded-For} que o cliente manda tem de ser <b>ignorado</b> e a
 * origem registrada tem de ser o peer real da conexao.
 *
 * <p>E o teste que a mutacao do Step 035.2.2 ataca: remover {@code internal-proxies} do
 * {@code application.yml} mantendo {@code native} devolve o default do Spring Boot, que confia em
 * toda faixa privada — inclusive {@code 127.0.0.1} — e este teste volta a ver o valor forjado.
 *
 * <p>Um teste so do caminho feliz nao verificaria nada: o {@code 201}/{@code 401} sai igual nos
 * dois mundos. A afirmacao esta no {@code ip} persistido, nao no status.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrigemForaDoAllowlistIT {

    private static final String ORIGEM_FORJADA = "203.0.113.7";

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    private int porta;

    @Autowired
    private LoginAttemptRepository loginAttempts;

    @BeforeEach
    @AfterEach
    void limpar() {
        loginAttempts.deleteAll();
    }

    @Test
    void xForwardedForDeOrigemNaoConfiavelEhIgnorado() {
        tentarLogin();

        assertThat(loginAttempts.findAll())
                .singleElement()
                .extracting(LoginAttempt::getIp)
                .as("com allowlist vazio o header e do cliente, nao de um proxy: nao pode virar a origem")
                .isNotEqualTo(ORIGEM_FORJADA)
                .isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
    }

    private void tentarLogin() {
        RestAssured.given()
                .port(porta)
                .contentType(ContentType.JSON)
                .header("X-Forwarded-For", ORIGEM_FORJADA)
                .body("{\"username\":\"origem-fora@sep.test\",\"password\":\"senha-errada\"}")
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401);
    }
}
