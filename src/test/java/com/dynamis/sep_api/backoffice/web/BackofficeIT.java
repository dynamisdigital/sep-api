package com.dynamis.sep_api.backoffice.web;

import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ComentarioInternoRepository;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ReprocessoRepository;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * E2E da Sprint 14 (Task 14.9) — fluxo completo do backoffice.
 *
 * <p>Cenarios obrigatorios cobertos:
 *
 * <ul>
 *   <li>Onboarding REPROVADO -> listener cria item ALTA na fila
 *   <li>Operador BACKOFFICE: lista -> assume -> comenta -> resolve com justificativa + step-up
 *   <li>Resolver sem step-up + MFA habilitado -> 403
 *   <li>BACKOFFICE tenta registrar parecer credito -> 403 (sem role FINANCEIRO)
 *   <li>BACKOFFICE tenta registrar recebimento cobranca -> 403
 *   <li>Dashboard retorna metricas consolidadas
 *   <li>Idempotencia: 2 eventos identicos -> 1 item ativo
 *   <li>Audit log gravado pra ITEM_FILA_CRIADO + ITEM_ASSUMIDO + ITEM_RESOLVIDO
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BackofficeIT {

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

    @Autowired
    ItemFilaOperacionalRepository itemRepository;

    @Autowired
    ComentarioInternoRepository comentarioRepository;

    @Autowired
    ReprocessoRepository reprocessoRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    AuditLogSegurancaRepository auditRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    PlatformTransactionManager txManager;

    @Autowired
    StepUpTokenService stepUpTokenService;

    @Autowired
    org.springframework.core.env.Environment environment;

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
            throw new IllegalStateException("BackofficeIT deve rodar apenas no banco sep_test; URL: " + url);
        }
        reprocessoRepository.deleteAll();
        comentarioRepository.deleteAll();
        itemRepository.deleteAll();
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

    private void publicarEventoEmTx(Object event) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.execute(status -> {
            eventPublisher.publishEvent(event);
            return null;
        });
    }

    private static void pollUntil(Supplier<Boolean> cond, String desc) {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(cond.get())) return;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            }
        }
        throw new AssertionError("Timeout aguardando: " + desc);
    }

    // ============== cenarios ==============

    @Test
    void onboardingReprovado_listenerCriaItemAltaNaFila() {
        Autenticado dono = criarELogar(Role.CLIENTE, false);
        UUID solicitacaoId = UUID.randomUUID();

        publicarEventoEmTx(
                new OnboardingFinalizadoEvent(solicitacaoId, dono.id(), StatusOnboarding.REPROVADO, "ext-id-1"));

        pollUntil(() -> itemRepository.count() == 1, "item da fila criado");
        var item = itemRepository.findAll().get(0);
        assertThat(item.getTipo()).isEqualTo(TipoItemFila.ONBOARDING_ERRO);
        assertThat(item.getPrioridade()).isEqualTo(PrioridadeItem.ALTA);
        assertThat(item.getStatus()).isEqualTo(StatusItemFila.ABERTO);
        assertThat(item.getEntidadeId()).isEqualTo(solicitacaoId);
    }

    @Test
    void operadorBackoffice_listaAssumeComentaResolveComStepUp() {
        Autenticado op = criarELogar(Role.BACKOFFICE, true);
        UUID solicitacaoId = UUID.randomUUID();
        publicarEventoEmTx(new OnboardingFinalizadoEvent(
                solicitacaoId, UUID.randomUUID(), StatusOnboarding.REPROVADO, "ext-id-2"));
        pollUntil(() -> itemRepository.count() == 1, "item criado");
        UUID itemId = itemRepository.findAll().get(0).getId();

        // 1. lista
        RestAssured.given()
                .header("Authorization", "Bearer " + op.token())
                .when()
                .get("/api/v1/backoffice/fila")
                .then()
                .statusCode(200);

        // 2. assume
        RestAssured.given()
                .header("Authorization", "Bearer " + op.token())
                .when()
                .post("/api/v1/backoffice/fila/{id}/assumir", itemId)
                .then()
                .statusCode(200)
                .body("status", containsString("EM_TRATAMENTO"));

        // 3. comenta
        RestAssured.given()
                .header("Authorization", "Bearer " + op.token())
                .contentType(ContentType.JSON)
                .body("{\"conteudo\":\"Em contato com tomador para regularizar\"}")
                .when()
                .post("/api/v1/backoffice/fila/{id}/comentarios", itemId)
                .then()
                .statusCode(201);

        // 4. resolve com step-up
        RestAssured.given()
                .header("Authorization", "Bearer " + op.token())
                .header("X-Step-Up-Token", emitirStepUp(op.id()))
                .contentType(ContentType.JSON)
                .body("{\"justificativa\":\"Documento validado manualmente apos contato com tomador\"}")
                .when()
                .patch("/api/v1/backoffice/fila/{id}/resolver", itemId)
                .then()
                .statusCode(200)
                .body("status", containsString("RESOLVIDO"));

        // audit registrado — 4 tipos exigidos pelo step (fix review Task 14.9)
        pollUntil(
                () -> {
                    var tipos = auditRepository.findAll().stream()
                            .map(a -> a.getTipo())
                            .collect(java.util.stream.Collectors.toSet());
                    return tipos.contains(TipoEventoSeguranca.ITEM_FILA_CRIADO)
                            && tipos.contains(TipoEventoSeguranca.ITEM_ASSUMIDO)
                            && tipos.contains(TipoEventoSeguranca.COMENTARIO_REGISTRADO)
                            && tipos.contains(TipoEventoSeguranca.ITEM_RESOLVIDO);
                },
                "audit 4 tipos do happy path");
    }

    @Test
    void resolver_semStepUp_comMfaHabilitado_403() {
        Autenticado op = criarELogar(Role.BACKOFFICE, true);
        UUID solicitacaoId = UUID.randomUUID();
        publicarEventoEmTx(new OnboardingFinalizadoEvent(
                solicitacaoId, UUID.randomUUID(), StatusOnboarding.REPROVADO, "ext-id-3"));
        pollUntil(() -> itemRepository.count() == 1, "item criado");
        UUID itemId = itemRepository.findAll().get(0).getId();

        RestAssured.given()
                .header("Authorization", "Bearer " + op.token())
                .contentType(ContentType.JSON)
                .body("{\"justificativa\":\"Justificativa com tamanho minimo aceitavel\"}")
                .when()
                .patch("/api/v1/backoffice/fila/{id}/resolver", itemId)
                .then()
                .statusCode(403);
    }

    @Test
    void backoffice_tentaRegistrarParecerCredito_403() {
        Autenticado op = criarELogar(Role.BACKOFFICE, false);

        RestAssured.given()
                .header("Authorization", "Bearer " + op.token())
                .contentType(ContentType.JSON)
                .body("{\"decisao\":\"APROVAR\",\"justificativa\":\"Justificativa com tamanho minimo de 10 chars\"}")
                .when()
                .post("/api/v1/credito/propostas/{id}/parecer", UUID.randomUUID())
                .then()
                .statusCode(403);
    }

    @Test
    void backoffice_tentaRegistrarRecebimentoCobranca_403() {
        Autenticado op = criarELogar(Role.BACKOFFICE, false);

        RestAssured.given()
                .header("Authorization", "Bearer " + op.token())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body(
                        "{\"valorRecebido\":100.00,\"meioPagamento\":\"PIX\",\"dataRecebimento\":\"2026-05-26T12:00:00Z\"}")
                .when()
                .post("/api/v1/cobranca/parcelas/{id}/recebimentos", UUID.randomUUID())
                .then()
                .statusCode(403);
    }

    @Test
    void dashboard_retornaMetricasConsolidadas() {
        Autenticado op = criarELogar(Role.BACKOFFICE, false);

        // Popula massa minima: 2 itens via eventos (REPROVADO e PENDENCIA) — fix review Task 14.9
        publicarEventoEmTx(new OnboardingFinalizadoEvent(
                UUID.randomUUID(), UUID.randomUUID(), StatusOnboarding.REPROVADO, "ext-d-1"));
        publicarEventoEmTx(new OnboardingFinalizadoEvent(
                UUID.randomUUID(), UUID.randomUUID(), StatusOnboarding.PENDENCIA, "ext-d-2"));
        pollUntil(() -> itemRepository.count() == 2, "2 itens criados pra dashboard");

        RestAssured.given()
                .header("Authorization", "Bearer " + op.token())
                .when()
                .get("/api/v1/backoffice/dashboard")
                .then()
                .statusCode(200)
                // estrutura completa — todos os campos do DashboardBackoffice present
                .body("geradoEm", org.hamcrest.Matchers.notNullValue())
                .body("contadoresPorTipo", org.hamcrest.Matchers.notNullValue())
                .body("contadoresPorPrioridade", org.hamcrest.Matchers.notNullValue())
                .body("contadoresPorStatus", org.hamcrest.Matchers.notNullValue())
                // Fixa a FORMA no fio (Sprint 34 Task 34.6): o Spring Boot desliga
                // WRITE_DURATIONS_AS_TIMESTAMPS, entao Duration sai ISO-8601 e o OpenAPI o documenta
                // como string. Um notNullValue() aceitava numero e string igualmente, e foi por isso
                // que anotar o schema como number passou pela suite inteira.
                .body("tempoMedioResolucao30d", org.hamcrest.Matchers.matchesPattern("^P.*"))
                .body("itensCriticosAbertosMais48h", org.hamcrest.Matchers.notNullValue())
                .body("topCincoTiposMaisFrequentes", org.hamcrest.Matchers.notNullValue())
                .body("recebimentosDoDia", org.hamcrest.Matchers.notNullValue())
                .body("inadimplenciaTotal", org.hamcrest.Matchers.notNullValue())
                .body("inadimplenciaTotal.valorTotal", org.hamcrest.Matchers.notNullValue())
                .body("inadimplenciaTotal.numeroParcelas", org.hamcrest.Matchers.notNullValue())
                .body("propostasPorStatus", org.hamcrest.Matchers.notNullValue())
                // metricas refletem a massa criada — 2 itens, prioridades ALTA+MEDIA
                .body("contadoresPorTipo.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(2))
                .body("contadoresPorPrioridade.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(2))
                .body("contadoresPorStatus.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1));
    }

    @Test
    void idempotencia_doisEventosIdenticos_geramUmUnicoItem() {
        UUID solicitacaoId = UUID.randomUUID();
        OnboardingFinalizadoEvent ev =
                new OnboardingFinalizadoEvent(solicitacaoId, UUID.randomUUID(), StatusOnboarding.REPROVADO, "ext-id-4");

        publicarEventoEmTx(ev);
        pollUntil(() -> itemRepository.count() == 1, "primeiro item criado");

        // 2o evento — listener vai disparar mas criarSeAusente retorna empty (nao cria nem
        // publica audit). Sem sinal positivo a aguardar; usa pollUntil invertido garantindo
        // que count nunca cresceu acima de 1 em 1.5s (fix review Task 14.9 — substitui sleep
        // direto por barreira ativa de assertion).
        publicarEventoEmTx(ev);
        long deadline = System.currentTimeMillis() + 1500L;
        while (System.currentTimeMillis() < deadline) {
            assertThat(itemRepository.count()).isEqualTo(1);
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            }
        }
        assertThat(itemRepository.count()).isEqualTo(1);
    }
}
