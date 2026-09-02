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
 * Lado <b>de dentro</b> do allowlist de proxy (Sprint 35 Task 35.2). Declara o loopback como proxy
 * confiavel, que e o papel que o balanceador tera na Fase 5, e ai o {@code X-Forwarded-For}
 * <b>tem</b> de ser respeitado — senao a troca de estrategia teria custo sem beneficio: todo mundo
 * atras do balanceador compartilharia um unico IP e o rate limit por IP viraria global.
 *
 * <p>Existe para provar o outro lado de {@link OrigemForaDoAllowlistIT}. Um allowlist que ignora o
 * header em qualquer situacao passaria naquele teste e reprovaria neste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrigemDentroDoAllowlistIT {

    private static final String ORIGEM_REAL_DO_CLIENTE = "203.0.113.7";

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("server.tomcat.remoteip.internal-proxies", () -> "127\\.0\\.0\\.1|0:0:0:0:0:0:0:1");
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
    void xForwardedForDeProxyConfiavelEhRespeitado() {
        RestAssured.given()
                .port(porta)
                .contentType(ContentType.JSON)
                .header("X-Forwarded-For", ORIGEM_REAL_DO_CLIENTE)
                .body("{\"username\":\"origem-dentro@sep.test\",\"password\":\"senha-errada\"}")
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401);

        assertThat(loginAttempts.findAll())
                .singleElement()
                .extracting(LoginAttempt::getIp)
                .as("vindo de proxy confiavel o header carrega a origem real do cliente")
                .isEqualTo(ORIGEM_REAL_DO_CLIENTE);
    }
}
