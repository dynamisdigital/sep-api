package com.dynamis.sep_api.credito.web;

import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.infrastructure.persistence.ConsentimentoOpenFinanceRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.DecisaoCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.MovimentacaoOpenFinanceRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ParecerCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.RegraCreditoAvaliadaRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ScoreInternoRepository;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaPldRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E backend do ciclo Open Finance (Sprint 9 Task 9.8). Sobe Spring Boot RANDOM_PORT + Postgres
 * sep_test.
 *
 * <p>Cobertura obrigatoria do spec:
 *
 * <ul>
 *   <li>fluxo feliz: cliente PRE_APROVADA -> inicia consentimento -> webhook AUTORIZADO ->
 *       dados recebidos (fake provider) -> reavaliacao automatica -> audit eventos;
 *   <li>callback NEGADO marca status; score nao muda;
 *   <li>cliente alheio tenta iniciar consentimento -> 403;
 *   <li>webhook HMAC invalido -> 401;
 *   <li>webhook sem Idempotency-Key -> 400;
 *   <li>webhook idempotente — 2x mesma key sem duplicar audit;
 *   <li>reavaliacao automatica NAO executa em proposta APROVADA.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenFinanceIT {

    private static final String WEBHOOK_SECRET = "dev-open-finance-webhook-secret-change-me";

    @DynamicPropertySource
    static void configurarTest(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
        registry.add("app.open-finance.provider", () -> "fake");
        registry.add("app.webhooks.secrets.celcoin-open-finance", () -> WEBHOOK_SECRET);
    }

    @LocalServerPort
    int port;

    @Autowired
    PropostaCreditoRepository propostaRepository;

    @Autowired
    ConsentimentoOpenFinanceRepository consentimentoRepository;

    @Autowired
    MovimentacaoOpenFinanceRepository movimentacaoRepository;

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
            throw new IllegalStateException("OpenFinanceIT exige sep_test; URL atual: " + url);
        }
        movimentacaoRepository.deleteAll();
        consentimentoRepository.deleteAll();
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

    private record Autenticado(UUID id, String email, String token) {}

    private Autenticado criarClienteELogar() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "cliente-" + suffix + "@sep.test";
        String senha = "senha-passphrase-segura";
        String id = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + senha + "\"}")
                .when()
                .post("/api/v1/usuarios")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        String token = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + senha + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        return new Autenticado(UUID.fromString(id), email, token);
    }

    private static final String[] CPFS_VALIDOS = {
        "52998224725", "11144477735", "87748248800", "39053344705", "12345678909"
    };

    private final java.util.concurrent.atomic.AtomicInteger cpfCursor = new java.util.concurrent.atomic.AtomicInteger();

    private UUID criarOnboardingAprovadoFinal(UUID tomadorId) {
        String cpf = CPFS_VALIDOS[cpfCursor.getAndIncrement() % CPFS_VALIDOS.length];
        SolicitacaoOnboarding s =
                SolicitacaoOnboarding.criarPessoa(tomadorId, new Cpf(cpf), "Tomador Teste", LocalDate.of(1990, 1, 1));
        s.registrarDocumentoEnviado();
        s.marcarEmVerificacao("fake-ext-" + UUID.randomUUID());
        s.finalizar(StatusOnboarding.APROVADO);
        s.marcarAprovadoFinal();
        return solicitacaoRepository.saveAndFlush(s).getId();
    }

    private String criarProposta(String token, UUID onboardingId) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"solicitacaoOnboardingId\":\"" + onboardingId
                        + "\",\"tipoOperacao\":\"OUTROS\",\"valorSolicitado\":10000.00,\"prazoMeses\":12}")
                .when()
                .post("/api/v1/credito/propostas")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String iniciarConsentimento(String token, UUID propostaId) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"cpfCnpjTomador\":\"52998224725\",\"redirectUri\":\"https://app.sep/cb\"}")
                .when()
                .post("/api/v1/credito/propostas/" + propostaId + "/open-finance/consentimento")
                .then()
                .statusCode(201)
                .extract()
                .path("consentimentoId");
    }

    private static String computeHmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

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

    private void aguardarProposta(UUID propostaId, StatusProposta alvo) {
        pollUntil(
                () -> propostaRepository.findById(propostaId).orElseThrow().getStatus() == alvo,
                "proposta status=" + alvo);
    }

    private ConsentimentoOpenFinance aguardarConsentimento(UUID consentimentoId, StatusConsentimento alvo) {
        pollUntil(
                () -> consentimentoRepository
                                .findById(consentimentoId)
                                .orElseThrow()
                                .getStatus()
                        == alvo,
                "consentimento status=" + alvo);
        return consentimentoRepository.findById(consentimentoId).orElseThrow();
    }

    // ============== Fluxo feliz ==============

    @Test
    void fluxoFelizConsentimentoAutorizadoReavaliaScoreEPublicaAudit() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingAprovadoFinal(cliente.id());
        String propostaIdStr = criarProposta(cliente.token(), onbId);
        UUID propostaId = UUID.fromString(propostaIdStr);
        aguardarProposta(propostaId, StatusProposta.PRE_APROVADA);

        String consentimentoIdStr = iniciarConsentimento(cliente.token(), propostaId);
        UUID consentimentoId = UUID.fromString(consentimentoIdStr);

        ConsentimentoOpenFinance consent =
                consentimentoRepository.findById(consentimentoId).orElseThrow();
        String idExterno = consent.getIdExternoCelcoin();
        assertThat(idExterno).startsWith("fake-of-");

        // Provider envia callback consent.authorized
        String payload = "{\"type\":\"consent.authorized\",\"consent_id\":\"" + idExterno + "\"}";
        RestAssured.given()
                .header("Idempotency-Key", "wh-" + UUID.randomUUID())
                .header("X-Webhook-Signature", computeHmac(WEBHOOK_SECRET, payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/webhooks/celcoin/open-finance")
                .then()
                .statusCode(202);

        // AUTORIZADO -> listener dispara consulta -> snapshot persiste -> listener reavaliacao
        aguardarConsentimento(consentimentoId, StatusConsentimento.AUTORIZADO);
        pollUntilAsserted(() -> assertThat(movimentacaoRepository.findByConsentimentoId(consentimentoId))
                .isPresent());

        // Audit eventos OF
        pollUntilAsserted(() -> {
            assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                            cliente.id(), TipoEventoSeguranca.OPEN_FINANCE_CONSENTIMENTO_INICIADO))
                    .hasSize(1);
            assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                            cliente.id(), TipoEventoSeguranca.OPEN_FINANCE_AUTORIZADO))
                    .hasSize(1);
            assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                            cliente.id(), TipoEventoSeguranca.OPEN_FINANCE_DADOS_RECEBIDOS))
                    .hasSize(1);
            assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                            cliente.id(), TipoEventoSeguranca.OPEN_FINANCE_REAVALIACAO))
                    .hasSize(1);
        });
    }

    // ============== NEGADO ==============

    @Test
    void callbackNegadoMarcaStatusSemAlterarScore() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingAprovadoFinal(cliente.id());
        UUID propostaId = UUID.fromString(criarProposta(cliente.token(), onbId));
        aguardarProposta(propostaId, StatusProposta.PRE_APROVADA);

        UUID consentimentoId = UUID.fromString(iniciarConsentimento(cliente.token(), propostaId));
        String idExterno =
                consentimentoRepository.findById(consentimentoId).orElseThrow().getIdExternoCelcoin();
        int scoreAntes =
                scoreRepository.findByPropostaId(propostaId).orElseThrow().getValor();

        String payload = "{\"type\":\"consent.denied\",\"consent_id\":\"" + idExterno + "\"}";
        RestAssured.given()
                .header("Idempotency-Key", "wh-neg-" + UUID.randomUUID())
                .header("X-Webhook-Signature", computeHmac(WEBHOOK_SECRET, payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/webhooks/celcoin/open-finance")
                .then()
                .statusCode(202);

        aguardarConsentimento(consentimentoId, StatusConsentimento.NEGADO);
        // Score nao muda
        int scoreDepois =
                scoreRepository.findByPropostaId(propostaId).orElseThrow().getValor();
        assertThat(scoreDepois).isEqualTo(scoreAntes);
        // Sem snapshot persistido
        assertThat(movimentacaoRepository.findByConsentimentoId(consentimentoId))
                .isEmpty();

        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.OPEN_FINANCE_NEGADO))
                .hasSize(1));
    }

    // ============== Ownership ==============

    @Test
    void clienteAlheioIniciaConsentimento403() {
        Autenticado dono = criarClienteELogar();
        UUID onbId = criarOnboardingAprovadoFinal(dono.id());
        UUID propostaId = UUID.fromString(criarProposta(dono.token(), onbId));
        aguardarProposta(propostaId, StatusProposta.PRE_APROVADA);

        Autenticado outro = criarClienteELogar();
        RestAssured.given()
                .header("Authorization", "Bearer " + outro.token())
                .contentType(ContentType.JSON)
                .body("{\"cpfCnpjTomador\":\"52998224725\",\"redirectUri\":\"https://app.sep/cb\"}")
                .when()
                .post("/api/v1/credito/propostas/" + propostaId + "/open-finance/consentimento")
                .then()
                .statusCode(403);
    }

    // ============== Webhook security ==============

    @Test
    void webhookHmacInvalidoRetorna401() {
        String payload = "{\"type\":\"consent.authorized\",\"consent_id\":\"ext-x\"}";
        RestAssured.given()
                .header("Idempotency-Key", "wh-bad")
                .header("X-Webhook-Signature", "deadbeef")
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/webhooks/celcoin/open-finance")
                .then()
                .statusCode(401);
    }

    @Test
    void webhookSemIdempotencyKeyRetorna400() {
        String payload = "{\"type\":\"consent.authorized\",\"consent_id\":\"ext-x\"}";
        RestAssured.given()
                .header("X-Webhook-Signature", computeHmac(WEBHOOK_SECRET, payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/webhooks/celcoin/open-finance")
                .then()
                .statusCode(400);
    }

    @Test
    void webhookIdempotenteNaoDuplicaAuditNemSnapshot() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingAprovadoFinal(cliente.id());
        UUID propostaId = UUID.fromString(criarProposta(cliente.token(), onbId));
        aguardarProposta(propostaId, StatusProposta.PRE_APROVADA);

        UUID consentimentoId = UUID.fromString(iniciarConsentimento(cliente.token(), propostaId));
        String idExterno =
                consentimentoRepository.findById(consentimentoId).orElseThrow().getIdExternoCelcoin();
        String idemKey = "wh-idem-" + UUID.randomUUID();
        String payload = "{\"type\":\"consent.authorized\",\"consent_id\":\"" + idExterno + "\"}";
        String sig = computeHmac(WEBHOOK_SECRET, payload);

        // Primeira chamada
        RestAssured.given()
                .header("Idempotency-Key", idemKey)
                .header("X-Webhook-Signature", sig)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/webhooks/celcoin/open-finance")
                .then()
                .statusCode(202);
        aguardarConsentimento(consentimentoId, StatusConsentimento.AUTORIZADO);
        pollUntilAsserted(() -> assertThat(movimentacaoRepository.findByConsentimentoId(consentimentoId))
                .isPresent());

        long autorizadoAntes = auditLogRepository
                .findByUsuarioIdAndTipoOrderByDataEventoDesc(cliente.id(), TipoEventoSeguranca.OPEN_FINANCE_AUTORIZADO)
                .size();
        long dadosAntes = auditLogRepository
                .findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.OPEN_FINANCE_DADOS_RECEBIDOS)
                .size();

        // Segunda chamada com mesma idempotency-key — outbox bloqueia
        RestAssured.given()
                .header("Idempotency-Key", idemKey)
                .header("X-Webhook-Signature", sig)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/webhooks/celcoin/open-finance")
                .then()
                .statusCode(202);

        // Audit e snapshot nao duplicaram
        assertThat(auditLogRepository
                        .findByUsuarioIdAndTipoOrderByDataEventoDesc(
                                cliente.id(), TipoEventoSeguranca.OPEN_FINANCE_AUTORIZADO)
                        .size())
                .isEqualTo(autorizadoAntes);
        assertThat(auditLogRepository
                        .findByUsuarioIdAndTipoOrderByDataEventoDesc(
                                cliente.id(), TipoEventoSeguranca.OPEN_FINANCE_DADOS_RECEBIDOS)
                        .size())
                .isEqualTo(dadosAntes);
        // 1 unica movimentacao persistida (V18 unique)
        List<com.dynamis.sep_api.credito.domain.model.MovimentacaoOpenFinance> movs =
                movimentacaoRepository.findAll().stream()
                        .filter(m -> m.getConsentimentoId().equals(consentimentoId))
                        .toList();
        assertThat(movs).hasSize(1);
    }

    // ============== Reavaliacao nao roda em proposta final ==============

    @Test
    void reavaliacaoNaoExecutaEmPropostaAprovada() {
        Autenticado cliente = criarClienteELogar();
        UUID onbId = criarOnboardingAprovadoFinal(cliente.id());
        UUID propostaId = UUID.fromString(criarProposta(cliente.token(), onbId));
        aguardarProposta(propostaId, StatusProposta.PRE_APROVADA);

        // Inicia consentimento ANTES da aprovacao
        UUID consentimentoId = UUID.fromString(iniciarConsentimento(cliente.token(), propostaId));
        String idExterno =
                consentimentoRepository.findById(consentimentoId).orElseThrow().getIdExternoCelcoin();

        // Promove a APROVADA via FINANCEIRO
        FinanceiroFixture financeiro = criarFinanceiroDirect();
        String financeiroToken = login(financeiro);
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiroToken)
                .contentType(ContentType.JSON)
                .body("{\"decisao\":\"APROVAR\",\"justificativa\":\"Cliente OK para aprovacao manual\"}")
                .when()
                .post("/api/v1/credito/propostas/" + propostaId + "/parecer")
                .then()
                .statusCode(200);
        aguardarProposta(propostaId, StatusProposta.APROVADA);
        int scoreAntes =
                scoreRepository.findByPropostaId(propostaId).orElseThrow().getValor();

        // Webhook AUTORIZADO chega depois da aprovacao
        String payload = "{\"type\":\"consent.authorized\",\"consent_id\":\"" + idExterno + "\"}";
        RestAssured.given()
                .header("Idempotency-Key", "wh-late-" + UUID.randomUUID())
                .header("X-Webhook-Signature", computeHmac(WEBHOOK_SECRET, payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/webhooks/celcoin/open-finance")
                .then()
                .statusCode(202);

        // Consentimento marcado AUTORIZADO + dados recebidos, mas reavaliacao skipa (status final)
        aguardarConsentimento(consentimentoId, StatusConsentimento.AUTORIZADO);
        pollUntilAsserted(() -> assertThat(movimentacaoRepository.findByConsentimentoId(consentimentoId))
                .isPresent());

        // Proposta continua APROVADA + score nao mudou
        assertThat(propostaRepository.findById(propostaId).orElseThrow().getStatus())
                .isEqualTo(StatusProposta.APROVADA);
        assertThat(scoreRepository.findByPropostaId(propostaId).orElseThrow().getValor())
                .isEqualTo(scoreAntes);
        // Sem evento OPEN_FINANCE_REAVALIACAO
        assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.OPEN_FINANCE_REAVALIACAO))
                .isEmpty();
    }

    private record FinanceiroFixture(UUID id, String email, String senha) {}

    private FinanceiroFixture criarFinanceiroDirect() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "financeiro-" + suffix + "@sep.test";
        String senha = "senha-passphrase-segura";
        Usuario u = Usuario.criar(email, passwordEncoder.encode(senha), Role.FINANCEIRO);
        u = usuarioRepository.saveAndFlush(u);
        return new FinanceiroFixture(u.getId(), email, senha);
    }

    private String login(FinanceiroFixture f) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + f.email() + "\",\"password\":\"" + f.senha() + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }
}
