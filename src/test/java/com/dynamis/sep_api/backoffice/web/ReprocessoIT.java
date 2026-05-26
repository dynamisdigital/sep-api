package com.dynamis.sep_api.backoffice.web;

import com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ReprocessoRepository;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E da Sprint 14 (Task 14.9) — fluxo de reprocesso manual.
 *
 * <ul>
 *   <li>Webhook na Outbox -> operador re-dispara via API -> Reprocesso persistido com SUCESSO
 *   <li>Anti-abuso 3/24h -> 4o reprocesso retorna 429
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReprocessoIT {

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

    @Autowired ReprocessoRepository reprocessoRepository;
    @Autowired ItemFilaOperacionalRepository itemRepository;
    @Autowired WebhookEventLogRepository webhookRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired AuditLogSegurancaRepository auditRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StepUpTokenService stepUpTokenService;
    @Autowired org.springframework.core.env.Environment environment;

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
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.contains("sep_test")) {
            throw new IllegalStateException("ReprocessoIT deve rodar apenas no banco sep_test; URL: " + url);
        }
        reprocessoRepository.deleteAll();
        itemRepository.deleteAll();
        webhookRepository.deleteAll();
        auditRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    private record Autenticado(UUID id, String email, String token) {}

    private Autenticado criarELogar(Role role, boolean mfaHabilitado) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = role.name().toLowerCase() + "-" + suffix + "@sep.test";
        String senha = "senha-passphrase-segura";
        Usuario u = Usuario.criar(email, passwordEncoder.encode(senha), role);
        if (mfaHabilitado) {
            u.marcarMfaHabilitado();
        }
        u = usuarioRepository.saveAndFlush(u);
        String token = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + senha + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        return new Autenticado(u.getId(), email, token);
    }

    private String emitirStepUp(UUID usuarioId) {
        return stepUpTokenService.emitir(usuarioId).token();
    }

    private UUID criarWebhookOutbox() {
        WebhookEventLog ev = WebhookEventLog.registrar(
                "celcoin", "kyc.updated", "idem-" + UUID.randomUUID(), "sig", "{}");
        return webhookRepository.saveAndFlush(ev).getId();
    }

    @Test
    void reprocessarWebhook_persisteRegistroComSucesso() {
        Autenticado op = criarELogar(Role.BACKOFFICE, true);
        UUID webhookId = criarWebhookOutbox();

        RestAssured.given()
                .header("Authorization", "Bearer " + op.token())
                .header("X-Step-Up-Token", emitirStepUp(op.id()))
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/backoffice/reprocessos/webhook/{id}", webhookId)
                .then()
                .statusCode(201)
                .body("status", org.hamcrest.Matchers.equalTo("SUCESSO"));

        assertThat(reprocessoRepository.count()).isEqualTo(1);
        var r = reprocessoRepository.findAll().get(0);
        assertThat(r.getStatus()).isEqualTo(StatusReprocesso.SUCESSO);
        assertThat(r.getIdentificadorExterno()).isEqualTo(webhookId.toString());
    }

    @Test
    void quartoReprocesso_em24h_retorna429() {
        Autenticado op = criarELogar(Role.BACKOFFICE, true);
        UUID webhookId = criarWebhookOutbox();
        String token = emitirStepUp(op.id());

        // 3 reprocessos sequenciais (single-thread) — todos esperados em 201 (fix review Task 14.9).
        for (int i = 1; i <= 3; i++) {
            RestAssured.given()
                    .header("Authorization", "Bearer " + op.token())
                    .header("X-Step-Up-Token", emitirStepUp(op.id()))
                    .contentType(ContentType.JSON)
                    .body("{}")
                    .when()
                    .post("/api/v1/backoffice/reprocessos/webhook/{id}", webhookId)
                    .then()
                    .statusCode(201);
        }

        // 4o reprocesso -> 429 garantido
        RestAssured.given()
                .header("Authorization", "Bearer " + op.token())
                .header("X-Step-Up-Token", emitirStepUp(op.id()))
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/backoffice/reprocessos/webhook/{id}", webhookId)
                .then()
                .statusCode(429);
    }
}
