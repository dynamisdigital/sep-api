package com.dynamis.sep_api.identity.web;

import com.dynamis.sep_api.identity.domain.model.LoginAttemptStatus;
import com.dynamis.sep_api.identity.infrastructure.persistence.LoginAttemptRepository;
import com.dynamis.sep_api.identity.infrastructure.security.LockoutProperties;
import com.dynamis.sep_api.identity.infrastructure.security.RateLimitProperties;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova fim a fim que o account lockout chega ao cliente como {@code 423} (Sprint 33 Task 33.3).
 *
 * <p>Nao existia nenhum teste assegurando o {@code 423}: os unitarios param na excecao e o
 * {@code ApiExceptionHandler} nunca era exercitado por este caminho.
 *
 * <p>Sprint 34 Task 34.1: a mesma jornada passa a cobrir a trilha de auditoria fim a fim — o
 * {@code LOCKOUT} da transicao (que rodava em {@code REQUIRES_NEW} sem cobertura observavel) e o
 * {@code LOCKOUT_TENTATIVA_BARRADA} de cada tentativa recusada durante o bloqueio.
 *
 * <p><b>Nao</b> sobrescreve {@code app.security.rate-limit.*}, diferente dos demais ITs. O ponto do
 * teste e justamente que o rate limit default deixa a 6a tentativa alcancar o controller: com os
 * dois valores em 5 (como era ate a Sprint 33) esta classe receberia {@code 429}. Sobrescrever o
 * rate limit aqui destruiria a regressao.
 *
 * <p>Por isso a jornada esta em <b>um unico teste</b>: o {@code RateLimiterRegistry} do
 * {@code RateLimitFilter} vive na JVM e nao e reiniciado entre metodos, entao varios metodos
 * fazendo login somariam permits e esbarrariam no limite por motivo errado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LockoutLoginIT {

    private static final String SENHA = "senha-passphrase-segura";
    private static final String SENHA_ERRADA = "senha-errada-mas-longa-o-suficiente";

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    Environment environment;

    @Autowired
    RateLimitProperties rateLimitProperties;

    @Autowired
    LockoutProperties lockoutProperties;

    @Autowired
    AuditLogSegurancaRepository auditRepository;

    @Autowired
    LoginAttemptRepository loginAttemptRepository;

    private String username;
    private UUID usuarioId;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
        limpar();
        username = "lockout-" + UUID.randomUUID().toString().substring(0, 8) + "@sep.test";
        usuarioId = usuarioRepository
                .saveAndFlush(Usuario.criar(username, passwordEncoder.encode(SENHA), Role.CLIENTE))
                .getId();
    }

    @AfterEach
    void cleanup() {
        limpar();
    }

    private void limpar() {
        if (!environment.getProperty("spring.datasource.url", "").contains("sep_test")) {
            throw new IllegalStateException("LockoutLoginIT deve rodar apenas no banco sep_test");
        }
        usuarioRepository.deleteAll();
    }

    private Response tentarLogin(String senha) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"" + senha + "\"}")
                .when()
                .post("/api/v1/auth/login");
    }

    /**
     * A invariante que torna o {@code 423} alcancavel. Barata e deterministica: nao consome permits
     * e falha no minuto em que alguem baixar o rate limit ate o limiar de lockout.
     */
    @Test
    void rateLimitDeLoginEhMaiorQueOLimiteDeLockout() {
        assertThat(rateLimitProperties.getLoginPerMinutePerIp())
                .as("com rate limit <= max-attempts o filtro barra a tentativa que responderia 423")
                .isGreaterThan(lockoutProperties.getMaxAttempts());
        assertThat(rateLimitProperties.getTotpVerifyPerMinutePerIp())
                .as("VerificarTotpUseCase tambem chama lockoutService.verificar()")
                .isGreaterThan(lockoutProperties.getMaxAttempts());
    }

    @Test
    void cincoSenhasErradasBloqueiamEAProximaTentativaRecebe423() {
        for (int i = 1; i <= 5; i++) {
            assertThat(tentarLogin(SENHA_ERRADA).statusCode())
                    .as("tentativa %d ainda e credencial invalida: verificar() roda antes de registrar", i)
                    .isEqualTo(401);
        }

        Response sexta = tentarLogin(SENHA_ERRADA);

        assertThat(sexta.statusCode())
                .as("a 6a tentativa precisa alcancar o controller; 429 aqui significa rate limit <= max-attempts")
                .isEqualTo(423);
        assertThat(sexta.jsonPath().getInt("status")).isEqualTo(423);
        assertThat(sexta.jsonPath().getString("error")).isEqualTo("Locked");
        assertThat(sexta.jsonPath().getString("path")).isEqualTo("/api/v1/auth/login");
        assertThat(sexta.jsonPath().getString("message")).contains("bloqueada");

        assertThat(tentarLogin(SENHA).statusCode())
                .as("o lockout e verificado antes da credencial, entao a senha certa nao desbloqueia")
                .isEqualTo(423);

        // Sprint 34 Task 34.1: os dois fatos sao distintos e ambos precisam de rastro. Filtra por
        // usuarioId porque limpar() nao apaga audit_log_seguranca — por tipo pegaria outras classes.
        pollUntilAsserted(() -> {
            assertThat(auditRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                            usuarioId, TipoEventoSeguranca.LOCKOUT))
                    .as("transicao para bloqueada: sai uma vez, na 5a falha")
                    .hasSize(1);
            assertThat(auditRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                            usuarioId, TipoEventoSeguranca.LOCKOUT_TENTATIVA_BARRADA))
                    .as("tentativa durante o bloqueio: sai a cada recusa, aqui a 6a e a 7a")
                    .hasSize(2);
        });

        assertThat(loginAttemptRepository.findByUsuarioId(usuarioId))
                .filteredOn(tentativa -> tentativa.getStatus() == LoginAttemptStatus.CONTA_BLOQUEADA)
                .as("ate a Sprint 33 a tentativa barrada nao gerava linha nenhuma em login_attempt")
                .hasSize(2);
    }

    /** Copia do padrao replicado em 7 ITs (ver {@code CarteiraCredoraIT}). */
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
        throw ultimo != null ? ultimo : new AssertionError("Timeout sem assercao");
    }
}
