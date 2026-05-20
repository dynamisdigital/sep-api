package com.dynamis.sep_api.credito.web;

import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.infrastructure.persistence.DecisaoCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ParecerCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.RegraCreditoAvaliadaRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ScoreInternoRepository;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaPldRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.audit.AuditLogSeguranca;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E backend do fluxo de credito (Sprint 8 Task 8.8). Sobe Spring Boot completo em RANDOM_PORT +
 * Postgres local (profile {@code dev}).
 *
 * <p>Cobertura obrigatoria do spec:
 *
 * <ul>
 *   <li>fluxo feliz APROVADO_FINAL -> PRE_APROVADA -> APROVADA (parecer manual);
 *   <li>rejeicao 422 sem onboarding aprovado;
 *   <li>valor acima do limite -> score reduzido -> EM_ANALISE -> parecer REJEITAR -> REJEITADA;
 *   <li>cliente lista apenas propria proposta;
 *   <li>FINANCEIRO sem step-up -> 403 (nesta sprint, MFA desabilitado bypassa step-up; cenario
 *       de step-up real coberto no @WebMvcTest);
 *   <li>FINANCEIRO com step-up valido (bypass mfa) -> 200 + audit log gravado;
 *   <li>promocao a FINANCEIRO por ADMIN -> 200;
 *   <li>promocao por nao-admin -> 403.
 * </ul>
 *
 * <p>FakeKycProvider eh default e onboarding APROVADO_FINAL eh montado via repositorio (atalho
 * pra evitar fluxo KYC completo neste IT). Auditoria de credito eh validada via
 * {@code audit_log_seguranca}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CreditoIT {

    @DynamicPropertySource
    static void configurarTest(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
        // Penalidades altas pra forcar EM_ANALISE quando 2 regras falham (1000 - 2*300 = 400)
        registry.add("app.credito.motor.penalidade-falha", () -> 300);
    }

    @LocalServerPort
    int port;

    @Autowired
    PropostaCreditoRepository propostaRepository;

    @Autowired
    com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository contratoRepository;

    @Autowired
    ScoreInternoRepository scoreRepository;

    @Autowired
    RegraCreditoAvaliadaRepository regraRepository;

    @Autowired
    ParecerCreditoRepository parecerRepository;

    @Autowired
    DecisaoCreditoRepository decisaoRepository;

    @Autowired
    SolicitacaoOnboardingRepository solicitacaoRepository;

    @Autowired
    ConsultaPldRepository consultaPldRepository;

    @Autowired
    WebhookEventLogRepository webhookEventLogRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    AuditLogSegurancaRepository auditLogRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    com.dynamis.sep_api.identity.application.service.StepUpTokenService stepUpTokenService;

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
        // Safeguard: garante que IT nunca apaga banco dev/producao por engano.
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.contains("sep_test")) {
            throw new IllegalStateException("CreditoIT deve rodar apenas no banco sep_test; URL atual: " + url + ". "
                    + "Crie o banco com: createdb -h localhost -U sep sep_test");
        }
        // contrato.proposta_id REFERENCES proposta_credito — limpar contratos primeiro (Sprint 10).
        // PropostaAprovadaListener gera contrato automaticamente quando IT aprova proposta.
        contratoRepository.deleteAll();
        // decisao_credito.parecer_id REFERENCES parecer_credito — limpar decisao primeiro
        decisaoRepository.deleteAll();
        parecerRepository.deleteAll();
        regraRepository.deleteAll();
        scoreRepository.deleteAll();
        propostaRepository.deleteAll();
        consultaPldRepository.deleteAll();
        solicitacaoRepository.deleteAll();
        webhookEventLogRepository.deleteAll();
        auditLogRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // ============== Fixtures ==============

    private record Autenticado(UUID id, String email, String token) {}

    private Autenticado criarClienteELogar() {
        return criarELogar(Role.CLIENTE, false);
    }

    private Autenticado criarAdminELogar() {
        return criarELogar(Role.ADMIN, false);
    }

    private Autenticado criarFinanceiroELogar() {
        // mfaHabilitado=false -> step-up aspect bypassa (Sprint 8 — pre-migracao MFA);
        // cenario com step-up real coberto via @WebMvcTest do controller.
        return criarELogar(Role.FINANCEIRO, false);
    }

    private Autenticado criarELogar(Role role, boolean mfaHabilitado) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = role.name().toLowerCase() + "-" + suffix + "@sep.test";
        String senha = "senha-passphrase-segura";

        // Cadastro publico cria CLIENTE; pra outros roles, instanciamos direto + persistimos.
        Usuario u;
        if (role == Role.CLIENTE) {
            String id = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body("{\"username\":\"" + email + "\",\"password\":\"" + senha + "\"}")
                    .when()
                    .post("/api/v1/usuarios")
                    .then()
                    .statusCode(201)
                    .extract()
                    .path("id");
            u = usuarioRepository.findById(UUID.fromString(id)).orElseThrow();
        } else {
            u = Usuario.criar(email, passwordEncoder.encode(senha), role);
            if (mfaHabilitado) {
                u.marcarMfaHabilitado();
            }
            u = usuarioRepository.saveAndFlush(u);
        }

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

    // Pool de CPFs validos pra IT — rotacionado por chamada pra evitar colisao no unique parcial
    // (uq_onboarding_cpf_ativo). Cleanup entre testes garante reuso seguro entre execucoes.
    private static final String[] CPFS_VALIDOS = {
        "52998224725", "11144477735", "87748248800", "39053344705", "12345678909"
    };

    private final java.util.concurrent.atomic.AtomicInteger cpfCursor = new java.util.concurrent.atomic.AtomicInteger();

    private UUID criarOnboardingAprovadoFinal(UUID tomadorId) {
        String cpf = CPFS_VALIDOS[cpfCursor.getAndIncrement() % CPFS_VALIDOS.length];
        SolicitacaoOnboarding s =
                SolicitacaoOnboarding.criarPessoa(tomadorId, new Cpf(cpf), "Tomador Teste", LocalDate.of(1990, 1, 1));
        // INICIADO -> DOCUMENTOS_RECEBIDOS -> EM_VERIFICACAO -> APROVADO -> APROVADO_FINAL
        s.registrarDocumentoEnviado();
        s.marcarEmVerificacao("fake-ext-" + UUID.randomUUID());
        s.finalizar(StatusOnboarding.APROVADO);
        s.marcarAprovadoFinal();
        return solicitacaoRepository.saveAndFlush(s).getId();
    }

    private String criarProposta(String token, UUID onboardingId, String valor, int prazo, String tipo) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"solicitacaoOnboardingId\":\"" + onboardingId + "\",\"tipoOperacao\":\"" + tipo
                        + "\",\"valorSolicitado\":" + valor + ",\"prazoMeses\":" + prazo + "}")
                .when()
                .post("/api/v1/credito/propostas")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private StatusProposta aguardarStatus(UUID propostaId, StatusProposta alvo) {
        pollUntil(() -> propostaRepository.findById(propostaId).orElseThrow().getStatus() == alvo, "status=" + alvo);
        return propostaRepository.findById(propostaId).orElseThrow().getStatus();
    }

    /** Poll simples ate {@code condicao} ser true ou 5s estourar. */
    private static void pollUntil(Supplier<Boolean> condicao, String descricao) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(condicao.get())) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
        }
        throw new AssertionError("Timeout aguardando: " + descricao);
    }

    /** Repete o {@code assercao} ate passar ou 5s estourar (eventually-consistent listeners). */
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
        throw ultimo != null ? ultimo : new AssertionError("Timeout sem assercao executada");
    }

    // ============== Fluxo feliz ==============

    @Test
    void fluxoFelizClienteCriaPropostaMotorPreAprovaFinanceiroAprovaEAuditoriaGravada() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingAprovadoFinal(cliente.id());
        String propostaId = criarProposta(cliente.token(), onbId, "10000.00", 12, "OUTROS");

        // Motor avalia AFTER_COMMIT em REQUIRES_NEW; aguarda PRE_APROVADA
        aguardarStatus(UUID.fromString(propostaId), StatusProposta.PRE_APROVADA);

        // Score persistido + audit PROPOSTA_AVALIADA_MOTOR (sincrono)
        assertThat(scoreRepository.findByPropostaId(UUID.fromString(propostaId)))
                .isPresent();
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.PROPOSTA_AVALIADA_MOTOR))
                .hasSize(1));

        // PROPOSTA_CRIADA via listener AFTER_COMMIT
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.PROPOSTA_CRIADA))
                .hasSize(1));

        // Financeiro registra parecer APROVAR (mfa=false -> bypass step-up)
        Autenticado financeiro = criarFinanceiroELogar();
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .contentType(ContentType.JSON)
                .body("{\"decisao\":\"APROVAR\",\"justificativa\":\"Cliente com bom historico interno\"}")
                .when()
                .post("/api/v1/credito/propostas/" + propostaId + "/parecer")
                .then()
                .statusCode(200);

        assertThat(propostaRepository
                        .findById(UUID.fromString(propostaId))
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StatusProposta.APROVADA);
        assertThat(decisaoRepository.findByPropostaId(UUID.fromString(propostaId)))
                .isPresent();

        // PARECER_REGISTRADO + PROPOSTA_APROVADA via listener AFTER_COMMIT
        pollUntilAsserted(() -> {
            assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                            financeiro.id(), TipoEventoSeguranca.PARECER_REGISTRADO))
                    .hasSize(1);
            assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                            cliente.id(), TipoEventoSeguranca.PROPOSTA_APROVADA))
                    .hasSize(1);
        });
    }

    // ============== Negativos ==============

    @Test
    void semOnboardingAprovadoRetorna422() {
        Autenticado cliente = criarClienteELogar();
        SolicitacaoOnboarding s = SolicitacaoOnboarding.criarPessoa(
                cliente.id(), new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1));
        // Status INICIADO (nao APROVADO_FINAL)
        UUID onbId = solicitacaoRepository.saveAndFlush(s).getId();

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body("{\"solicitacaoOnboardingId\":\"" + onbId
                        + "\",\"tipoOperacao\":\"OUTROS\",\"valorSolicitado\":10000.00,\"prazoMeses\":12}")
                .when()
                .post("/api/v1/credito/propostas")
                .then()
                .statusCode(422);
    }

    @Test
    void onboardingDeOutroTomador403() {
        Autenticado cliente = criarClienteELogar();
        Autenticado outroCliente = criarClienteELogar();
        UUID onbDoOutro = criarOnboardingAprovadoFinal(outroCliente.id());

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body("{\"solicitacaoOnboardingId\":\"" + onbDoOutro
                        + "\",\"tipoOperacao\":\"OUTROS\",\"valorSolicitado\":10000.00,\"prazoMeses\":12}")
                .when()
                .post("/api/v1/credito/propostas")
                .then()
                .statusCode(403);
    }

    @Test
    void clienteNaoCriaPropostaComoAdmin403() {
        Autenticado admin = criarAdminELogar();
        UUID onbId = criarOnboardingAprovadoFinal(admin.id());

        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .contentType(ContentType.JSON)
                .body("{\"solicitacaoOnboardingId\":\"" + onbId
                        + "\",\"tipoOperacao\":\"OUTROS\",\"valorSolicitado\":10000.00,\"prazoMeses\":12}")
                .when()
                .post("/api/v1/credito/propostas")
                .then()
                .statusCode(403);
    }

    @Test
    void clienteListaApenasProprias() {
        Autenticado cliente = criarClienteELogar();
        Autenticado outroCliente = criarClienteELogar();
        UUID onbA = criarOnboardingAprovadoFinal(cliente.id());
        UUID onbB = criarOnboardingAprovadoFinal(outroCliente.id());
        criarProposta(cliente.token(), onbA, "5000.00", 6, "OUTROS");
        criarProposta(outroCliente.token(), onbB, "5000.00", 6, "OUTROS");

        Integer total = RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/credito/propostas")
                .then()
                .statusCode(200)
                .extract()
                .path("totalElements");
        assertThat(total).isEqualTo(1);
    }

    @Test
    void financeiroListaPropostasDeQualquer() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingAprovadoFinal(cliente.id());
        criarProposta(cliente.token(), onbId, "5000.00", 6, "OUTROS");

        Autenticado financeiro = criarFinanceiroELogar();
        Integer total = RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get("/api/v1/credito/propostas")
                .then()
                .statusCode(200)
                .extract()
                .path("totalElements");
        assertThat(total).isGreaterThanOrEqualTo(1);
    }

    @Test
    void parecerComoClienteRetorna403() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingAprovadoFinal(cliente.id());
        String propostaId = criarProposta(cliente.token(), onbId, "10000.00", 12, "OUTROS");
        aguardarStatus(UUID.fromString(propostaId), StatusProposta.PRE_APROVADA);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body("{\"decisao\":\"APROVAR\",\"justificativa\":\"tentativa indevida\"}")
                .when()
                .post("/api/v1/credito/propostas/" + propostaId + "/parecer")
                .then()
                .statusCode(403);
    }

    @Test
    void listarRegrasComoClienteRetorna403() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingAprovadoFinal(cliente.id());
        String propostaId = criarProposta(cliente.token(), onbId, "10000.00", 12, "OUTROS");
        aguardarStatus(UUID.fromString(propostaId), StatusProposta.PRE_APROVADA);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/credito/propostas/" + propostaId + "/regras")
                .then()
                .statusCode(403);
    }

    @Test
    void listarRegrasComoFinanceiroRetornaTrilha() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingAprovadoFinal(cliente.id());
        String propostaId = criarProposta(cliente.token(), onbId, "10000.00", 12, "OUTROS");
        aguardarStatus(UUID.fromString(propostaId), StatusProposta.PRE_APROVADA);

        Autenticado financeiro = criarFinanceiroELogar();
        List<?> regras = RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get("/api/v1/credito/propostas/" + propostaId + "/regras")
                .then()
                .statusCode(200)
                .extract()
                .path("$");
        assertThat(regras).isNotEmpty();
    }

    // ============== Promocao role FINANCEIRO ==============

    @Test
    void adminPromoveUsuarioParaFinanceiro200EAudita() {
        Autenticado admin = criarAdminELogar();
        Autenticado cliente = criarClienteELogar();

        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .contentType(ContentType.JSON)
                .body("{\"role\":\"FINANCEIRO\"}")
                .when()
                .post("/api/v1/usuarios/" + cliente.id() + "/role")
                .then()
                .statusCode(200)
                .body("role", org.hamcrest.Matchers.equalTo("FINANCEIRO"));

        pollUntilAsserted(() -> {
            List<AuditLogSeguranca> logs = auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                    admin.id(), TipoEventoSeguranca.ROLE_ALTERADO);
            assertThat(logs).hasSize(1);
        });
    }

    @Test
    void promocaoPorNaoAdminRetorna403() {
        Autenticado cliente = criarClienteELogar();
        Autenticado outroCliente = criarClienteELogar();

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body("{\"role\":\"FINANCEIRO\"}")
                .when()
                .post("/api/v1/usuarios/" + outroCliente.id() + "/role")
                .then()
                .statusCode(403);
    }

    // ============== Step-up real (mfa habilitado) ==============

    @Test
    void financeiroComMfaHabilitadoSemStepUpTokenRetorna403() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingAprovadoFinal(cliente.id());
        String propostaId = criarProposta(cliente.token(), onbId, "10000.00", 12, "OUTROS");
        aguardarStatus(UUID.fromString(propostaId), StatusProposta.PRE_APROVADA);

        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .contentType(ContentType.JSON)
                .body("{\"decisao\":\"APROVAR\",\"justificativa\":\"Justificativa adequada do parecerista\"}")
                .when()
                .post("/api/v1/credito/propostas/" + propostaId + "/parecer")
                .then()
                .statusCode(403);
    }

    @Test
    void financeiroComMfaHabilitadoComStepUpTokenValido200() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingAprovadoFinal(cliente.id());
        String propostaId = criarProposta(cliente.token(), onbId, "10000.00", 12, "OUTROS");
        aguardarStatus(UUID.fromString(propostaId), StatusProposta.PRE_APROVADA);

        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        // Emite step-up token diretamente via service (bypass do fluxo TOTP — apenas testa o
        // contrato do aspect contra um token genuinamente valido + consumivel).
        String stepUpToken = stepUpTokenService.emitir(financeiro.id()).token();

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", stepUpToken)
                .contentType(ContentType.JSON)
                .body("{\"decisao\":\"APROVAR\",\"justificativa\":\"Cliente com bom historico interno\"}")
                .when()
                .post("/api/v1/credito/propostas/" + propostaId + "/parecer")
                .then()
                .statusCode(200);

        assertThat(propostaRepository
                        .findById(UUID.fromString(propostaId))
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StatusProposta.APROVADA);
    }

    // ============== Valor acima do limite -> EM_ANALISE -> REJEITAR manual -> REJEITADA ==============

    @Test
    void valorAcimaDoLimiteVaiParaEmAnaliseEFinanceiroRejeita() {
        // Penalidade-falha=300 (override em @DynamicPropertySource) + 2 regras falhas
        // (valor + prazo PF acima do limite) -> score 1000-600=400 -> EM_ANALISE
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingAprovadoFinal(cliente.id());
        String propostaId = criarProposta(cliente.token(), onbId, "60000.00", 13, "OUTROS");

        aguardarStatus(UUID.fromString(propostaId), StatusProposta.EM_ANALISE);

        Autenticado financeiro = criarFinanceiroELogar();
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .contentType(ContentType.JSON)
                .body("{\"decisao\":\"REJEITAR\",\"justificativa\":\"Valor incompativel com perfil PF do tomador\"}")
                .when()
                .post("/api/v1/credito/propostas/" + propostaId + "/parecer")
                .then()
                .statusCode(200);

        assertThat(propostaRepository
                        .findById(UUID.fromString(propostaId))
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StatusProposta.REJEITADA);
        assertThat(decisaoRepository.findByPropostaId(UUID.fromString(propostaId)))
                .isPresent();
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.PROPOSTA_REJEITADA))
                .hasSize(1));
    }

    // ============== Promocao role FINANCEIRO ==============

    @Test
    void adminNaoPodeAlterarPropriaRole403() {
        Autenticado admin = criarAdminELogar();

        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .contentType(ContentType.JSON)
                .body("{\"role\":\"CLIENTE\"}")
                .when()
                .post("/api/v1/usuarios/" + admin.id() + "/role")
                .then()
                .statusCode(403);
    }
}
