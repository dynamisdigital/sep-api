package com.dynamis.sep_api.identity.web;

import com.dynamis.sep_api.identity.domain.model.LoginAttempt;
import com.dynamis.sep_api.identity.infrastructure.persistence.LoginAttemptRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Base dos dois ITs de origem da request (Sprint 35 Task 35.2). Eles diferem em <b>uma</b> linha de
 * configuracao e uma assercao; todo o resto — porta, repositorio, limpeza guardada e a tentativa de
 * login — e identico.
 *
 * <p>Precisam continuar sendo <b>classes separadas</b>, e nao dois metodos: o
 * {@code @DynamicPropertySource} entra na chave de cache do contexto Spring, entao cada um recebe o
 * proprio {@code RemoteIpValve} — que e justamente a peca sob teste — e o proprio mapa de
 * limitadores.
 */
abstract class OrigemDaRequestITBase {

    protected static final String ORIGEM_NO_HEADER = "203.0.113.7";

    @LocalServerPort
    private int porta;

    @Autowired
    private LoginAttemptRepository loginAttempts;

    @Autowired
    private Environment environment;

    /**
     * Guarda o {@code deleteAll} contra banco errado, no padrao do {@code LockoutLoginIT}. Sem ela um
     * shell com {@code DB_NAME=sep_dev} exportado apagaria a trilha de login de desenvolvimento — o
     * default {@code sep_test} vem do {@code application-test.yml}, mas e so um default.
     */
    @BeforeEach
    @AfterEach
    void limpar() {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.contains("sep_test")) {
            throw new IllegalStateException(
                    getClass().getSimpleName() + " deve rodar apenas no banco sep_test; URL: " + url);
        }
        loginAttempts.deleteAll();
    }

    /** Falha de login com {@code X-Forwarded-For} forjado. O status e o mesmo nos dois cenarios — a
     * afirmacao de cada teste esta no {@code ip} persistido, nunca no status. */
    protected void tentarLogin(String username) {
        RestAssured.given()
                .port(porta)
                .contentType(ContentType.JSON)
                .header("X-Forwarded-For", ORIGEM_NO_HEADER)
                .body("{\"username\":\"" + username + "\",\"password\":\"senha-errada\"}")
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401);
    }

    protected List<LoginAttempt> tentativasRegistradas() {
        return loginAttempts.findAll();
    }

    protected String allowlistVigente() {
        return environment.getProperty("server.tomcat.remoteip.internal-proxies");
    }
}
