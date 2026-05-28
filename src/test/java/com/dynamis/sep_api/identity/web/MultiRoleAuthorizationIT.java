package com.dynamis.sep_api.identity.web;

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

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Regressao de autorizacao multi-role (Sprint 18 Task 18.3). Prova no nivel dos guards Spring
 * Security que um usuario FINANCEIRO + BACKOFFICE acessa endpoints de AMBAS as roles, enquanto
 * usuarios single-role recebem 403 onde nao tem permissao.
 *
 * <p>Endpoints usados:
 *
 * <ul>
 *   <li>{@code GET /api/v1/backoffice/fila} — {@code hasAnyRole(FINANCEIRO,BACKOFFICE,ADMIN)};
 *   <li>{@code GET /api/v1/credito/propostas/{id}/regras} — {@code hasAnyRole(FINANCEIRO,ADMIN)}
 *       (exclui BACKOFFICE). Autorizado mas sem proposta -> 404; negado -> 403.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MultiRoleAuthorizationIT {

    @DynamicPropertySource
    static void configurarTest(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

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
        if (!environment.getProperty("spring.datasource.url", "").contains("sep_test")) {
            throw new IllegalStateException("MultiRoleAuthorizationIT deve rodar apenas no banco sep_test");
        }
        usuarioRepository.deleteAll();
    }

    private static final String SENHA = "senha-passphrase-segura";

    private String logar(Set<Role> roles) {
        String email = "u-" + UUID.randomUUID().toString().substring(0, 8) + "@sep.test";
        Role primeira = roles.iterator().next();
        Usuario u = Usuario.criar(email, passwordEncoder.encode(SENHA), primeira);
        roles.forEach(u::adicionarRole);
        usuarioRepository.saveAndFlush(u);
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + SENHA + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    private int status(String token, String path) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(path)
                .then()
                .extract()
                .statusCode();
    }

    @Test
    void usuarioFinanceiroEBackofficeAcessaEndpointsDeAmbasAsRoles() {
        String token = logar(EnumSet.of(Role.FINANCEIRO, Role.BACKOFFICE));
        // backoffice: hasAnyRole(FINANCEIRO,BACKOFFICE,ADMIN) -> 200
        org.assertj.core.api.Assertions.assertThat(status(token, "/api/v1/backoffice/fila"))
                .isEqualTo(200);
        // credito regras: hasAnyRole(FINANCEIRO,ADMIN) -> autorizado (404 proposta inexistente, nao 403)
        org.assertj.core.api.Assertions.assertThat(
                        status(token, "/api/v1/credito/propostas/" + UUID.randomUUID() + "/regras"))
                .isEqualTo(404);
    }

    @Test
    void usuarioBackofficeUnicoNaoAcessaEndpointFinanceiro() {
        String token = logar(EnumSet.of(Role.BACKOFFICE));
        org.assertj.core.api.Assertions.assertThat(status(token, "/api/v1/backoffice/fila"))
                .isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(
                        status(token, "/api/v1/credito/propostas/" + UUID.randomUUID() + "/regras"))
                .isEqualTo(403);
    }

    @Test
    void usuarioClienteNaoAcessaBackoffice() {
        String token = logar(EnumSet.of(Role.CLIENTE));
        org.assertj.core.api.Assertions.assertThat(status(token, "/api/v1/backoffice/fila"))
                .isEqualTo(403);
    }
}
