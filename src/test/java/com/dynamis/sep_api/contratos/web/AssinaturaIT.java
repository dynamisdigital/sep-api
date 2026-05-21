package com.dynamis.sep_api.contratos.web;

import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.infrastructure.persistence.AceiteContratoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.DocumentoAssinadoBlobRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.DocumentoAssinadoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.EnvelopeAssinaturaRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.EventoAssinaturaRepository;
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
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E backend do ciclo completo de assinatura digital (Sprint 11 Task 11.9). Sobe Spring Boot
 * completo em RANDOM_PORT + Postgres local (profile {@code test}, banco {@code sep_test}).
 *
 * <p>Cobertura do spec §11.9:
 *
 * <ul>
 *   <li>Fluxo feliz com fake provider: contrato ACEITO -> listener dispara envio -> envelope
 *       ENVIADO -> webhook callback ASSINADO -> contrato ASSINADO + PDF assinado + audit;
 *   <li>Callback RECUSADO transiciona contrato RECUSADO + audit;
 *   <li>Webhook idempotente: mesmo payload duas vezes registra um efeito de negocio;
 *   <li>Webhook com HMAC invalido retorna 401;
 *   <li>Download do PDF assinado por nao-owner retorna 403;
 *   <li>Reenvio (POST /assinar) com envelope ja existente eh idempotente.
 * </ul>
 *
 * <p>Step-up: usuarios criados com {@code mfaHabilitado=false} — {@code StepUpEnforcementAspect}
 * bypassa o token (mesmo padrao do {@code ContratoIT} Sprint 10).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssinaturaIT {

    private static final String WEBHOOK_PATH = "/api/v1/webhooks/assinatura/clicksign";

    @DynamicPropertySource
    static void configurarTest(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
        // Auto-envio ativo (default) — listener ContratoAceitoListener dispara EnviarParaAssinatura
        // logo apos aceite, criando envelope via FakeAssinaturaDigitalProvider.
        registry.add("app.assinatura.auto-envio-pos-aceite", () -> "true");
    }

    @LocalServerPort
    int port;

    @Value("${app.webhooks.secrets.clicksign-assinatura}")
    String webhookSecret;

    @Autowired
    ContratoRepository contratoRepository;

    @Autowired
    AceiteContratoRepository aceiteContratoRepository;

    @Autowired
    EnvelopeAssinaturaRepository envelopeAssinaturaRepository;

    @Autowired
    EventoAssinaturaRepository eventoAssinaturaRepository;

    @Autowired
    DocumentoAssinadoRepository documentoAssinadoRepository;

    @Autowired
    DocumentoAssinadoBlobRepository documentoAssinadoBlobRepository;

    @Autowired
    PropostaCreditoRepository propostaRepository;

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
            throw new IllegalStateException("AssinaturaIT deve rodar apenas no banco sep_test; URL atual: " + url);
        }
        documentoAssinadoRepository.deleteAll();
        documentoAssinadoBlobRepository.deleteAll();
        eventoAssinaturaRepository.deleteAll();
        envelopeAssinaturaRepository.deleteAll();
        aceiteContratoRepository.deleteAll();
        contratoRepository.deleteAll();
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

    // ============== Fixtures (espelha ContratoIT Sprint 10) ==============

    private record Autenticado(UUID id, String email, String token) {}

    private Autenticado criarELogar(Role role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = role.name().toLowerCase() + "-" + suffix + "@sep.test";
        String senha = "senha-passphrase-segura";
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

    private static final String[] CPFS = {"52998224725", "11144477735", "87748248800", "39053344705", "12345678909"};
    private final AtomicInteger cpfCursor = new AtomicInteger();

    private UUID criarOnboardingAprovadoFinal(UUID tomadorId) {
        String cpf = CPFS[cpfCursor.getAndIncrement() % CPFS.length];
        SolicitacaoOnboarding s =
                SolicitacaoOnboarding.criarPessoa(tomadorId, new Cpf(cpf), "Tomador", LocalDate.of(1990, 1, 1));
        s.registrarDocumentoEnviado();
        s.marcarEmVerificacao("fake-ext-" + UUID.randomUUID());
        s.finalizar(StatusOnboarding.APROVADO);
        s.marcarAprovadoFinal();
        return solicitacaoRepository.saveAndFlush(s).getId();
    }

    private UUID criarPropostaAprovadaEAguardarContrato(Autenticado cliente) {
        UUID onbId = criarOnboardingAprovadoFinal(cliente.id());
        String propostaId = RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body("{\"solicitacaoOnboardingId\":\"" + onbId
                        + "\",\"tipoOperacao\":\"OUTROS\",\"valorSolicitado\":10000.00,\"prazoMeses\":12}")
                .when()
                .post("/api/v1/credito/propostas")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        UUID propId = UUID.fromString(propostaId);
        pollUntil(
                () -> propostaRepository.findById(propId).orElseThrow().getStatus() == StatusProposta.PRE_APROVADA,
                "PRE_APROVADA");
        Autenticado financeiro = criarELogar(Role.FINANCEIRO);
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .contentType(ContentType.JSON)
                .body("{\"decisao\":\"APROVAR\",\"justificativa\":\"Cliente com historico interno limpo\"}")
                .when()
                .post("/api/v1/credito/propostas/" + propId + "/parecer")
                .then()
                .statusCode(200);
        pollUntil(
                () -> propostaRepository.findById(propId).orElseThrow().getStatus() == StatusProposta.APROVADA,
                "APROVADA");
        pollUntil(() -> contratoRepository.findByPropostaId(propId).isPresent(), "contrato gerado");
        return propId;
    }

    /** Cria contrato, aguarda envelope e devolve {contratoId, idEnvelopeExterno}. */
    private EnvelopeCriado prepararContratoAceitoComEnvelope(Autenticado cliente) {
        UUID propostaId = criarPropostaAprovadaEAguardarContrato(cliente);
        UUID contratoId =
                contratoRepository.findByPropostaId(propostaId).orElseThrow().getId();

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .patch("/api/v1/contratos/" + contratoId + "/aceite")
                .then()
                .statusCode(200);

        // Listener AFTER_COMMIT em transacao separada cria envelope
        pollUntil(
                () -> envelopeAssinaturaRepository.findByContratoId(contratoId).isPresent(),
                "envelope criado pelo listener");
        String idEnvelopeExterno = envelopeAssinaturaRepository
                .findByContratoId(contratoId)
                .orElseThrow()
                .getIdEnvelopeExterno();
        return new EnvelopeCriado(contratoId, idEnvelopeExterno);
    }

    private record EnvelopeCriado(UUID contratoId, String idEnvelopeExterno) {}

    private static String payloadClicksign(String eventName, String documentKey) {
        return "{\"event\":{\"name\":\"" + eventName + "\",\"occurred_at\":\"2026-05-21T15:00:00Z\"},"
                + "\"document\":{\"key\":\"" + documentKey + "\"}}";
    }

    private String hmacSha256Hex(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC fail", ex);
        }
    }

    private Response postWebhook(String payload, String contentHmac) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Content-Hmac", contentHmac)
                .body(payload)
                .when()
                .post(WEBHOOK_PATH);
    }

    private static void pollUntil(Supplier<Boolean> cond, String desc) {
        long deadline = System.currentTimeMillis() + 5_000L;
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
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
        throw ultimo != null ? ultimo : new AssertionError("Timeout sem assercao");
    }

    // ============== Cenarios E2E ==============

    @Test
    void fluxoFeliz_aceiteDisparaEnvioCallbackAssinadoContratoAssinadoEAudit() {
        Autenticado cliente = criarELogar(Role.CLIENTE);
        EnvelopeCriado env = prepararContratoAceitoComEnvelope(cliente);

        // Envelope nasce ENVIADO + audit ASSINATURA_ENVIADA
        assertThat(envelopeAssinaturaRepository
                        .findByContratoId(env.contratoId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StatusEnvelope.ENVIADO);
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.CCB_GERADA))
                .hasSize(1));
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.ASSINATURA_ENVIADA))
                .hasSize(1));

        // Callback ASSINADO via webhook (HMAC valido)
        String payload = payloadClicksign("sign", env.idEnvelopeExterno());
        postWebhook(payload, "sha256=" + hmacSha256Hex(payload)).then().statusCode(202);

        // Contrato ASSINADO + DocumentoAssinado persistido + audit ASSINATURA_ASSINADA
        pollUntilAsserted(() -> assertThat(contratoRepository
                        .findById(env.contratoId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StatusFormalizacao.ASSINADO));
        assertThat(documentoAssinadoRepository.findByEnvelopeId(envelopeAssinaturaRepository
                        .findByContratoId(env.contratoId())
                        .orElseThrow()
                        .getId()))
                .isPresent();
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.ASSINATURA_ASSINADA))
                .hasSize(1));
    }

    @Test
    void callbackRecusado_transicionaContratoRecusadoEAudit() {
        Autenticado cliente = criarELogar(Role.CLIENTE);
        EnvelopeCriado env = prepararContratoAceitoComEnvelope(cliente);

        String payload = payloadClicksign("refuse", env.idEnvelopeExterno());
        postWebhook(payload, "sha256=" + hmacSha256Hex(payload)).then().statusCode(202);

        pollUntilAsserted(() -> assertThat(contratoRepository
                        .findById(env.contratoId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StatusFormalizacao.RECUSADO));
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.ASSINATURA_RECUSADA))
                .hasSize(1));
    }

    @Test
    void webhookIdempotente_mesmoPayloadDuasVezesUmEfeito() {
        Autenticado cliente = criarELogar(Role.CLIENTE);
        EnvelopeCriado env = prepararContratoAceitoComEnvelope(cliente);

        String payload = payloadClicksign("sign", env.idEnvelopeExterno());
        String hmac = "sha256=" + hmacSha256Hex(payload);

        postWebhook(payload, hmac).then().statusCode(202);
        postWebhook(payload, hmac).then().statusCode(202);

        // Idempotency-Key derivado do payload eh igual nas duas chamadas → outbox ignora 2a;
        // EventoAssinatura tem UNIQUE (envelope_id, id_evento_externo) — apenas 1 linha.
        pollUntilAsserted(() -> {
            UUID envelopeId = envelopeAssinaturaRepository
                    .findByContratoId(env.contratoId())
                    .orElseThrow()
                    .getId();
            assertThat(eventoAssinaturaRepository.findAll().stream()
                            .filter(e -> e.getEnvelopeId().equals(envelopeId))
                            .count())
                    .isEqualTo(1L);
        });
        // Apenas um audit ASSINATURA_ASSINADA
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.ASSINATURA_ASSINADA))
                .hasSize(1));
    }

    @Test
    void webhookHmacInvalido_retorna401() {
        Autenticado cliente = criarELogar(Role.CLIENTE);
        EnvelopeCriado env = prepararContratoAceitoComEnvelope(cliente);

        String payload = payloadClicksign("sign", env.idEnvelopeExterno());
        postWebhook(payload, "sha256=deadbeef").then().statusCode(401);

        // Contrato preserva EM_ASSINATURA — nada processado
        assertThat(contratoRepository.findById(env.contratoId()).orElseThrow().getStatus())
                .isEqualTo(StatusFormalizacao.EM_ASSINATURA);
    }

    @Test
    void downloadDocumentoAssinado_naoOwnerRetorna403() {
        Autenticado cliente = criarELogar(Role.CLIENTE);
        Autenticado outroCliente = criarELogar(Role.CLIENTE);
        EnvelopeCriado env = prepararContratoAceitoComEnvelope(cliente);

        // Assinar primeiro
        String payload = payloadClicksign("sign", env.idEnvelopeExterno());
        postWebhook(payload, "sha256=" + hmacSha256Hex(payload)).then().statusCode(202);
        pollUntilAsserted(() -> assertThat(contratoRepository
                        .findById(env.contratoId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StatusFormalizacao.ASSINADO));

        RestAssured.given()
                .header("Authorization", "Bearer " + outroCliente.token())
                .when()
                .get("/api/v1/contratos/" + env.contratoId() + "/documento-assinado")
                .then()
                .statusCode(403);
    }

    @Test
    void downloadDocumentoAssinado_ownerRecebePdfComHeaders() {
        Autenticado cliente = criarELogar(Role.CLIENTE);
        EnvelopeCriado env = prepararContratoAceitoComEnvelope(cliente);

        String payload = payloadClicksign("sign", env.idEnvelopeExterno());
        postWebhook(payload, "sha256=" + hmacSha256Hex(payload)).then().statusCode(202);
        pollUntilAsserted(() -> assertThat(contratoRepository
                        .findById(env.contratoId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StatusFormalizacao.ASSINADO));

        Response resp = RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/contratos/" + env.contratoId() + "/documento-assinado");

        resp.then().statusCode(200).contentType("application/pdf");
        assertThat(resp.getHeader("X-Document-Hash-Sha256")).isNotBlank();
        assertThat(resp.getHeader("Content-Disposition"))
                .contains("attachment")
                .contains(env.contratoId().toString());
        assertThat(resp.asByteArray()).startsWith("%PDF".getBytes(StandardCharsets.UTF_8));

        // Audit DOCUMENTO_ASSINADO_BAIXADO grava ip + user-agent
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.DOCUMENTO_ASSINADO_BAIXADO))
                .hasSize(1));
    }

    @Test
    void reenvioPostAssinar_idempotenteNaoCriaNovoEnvelope() {
        Autenticado cliente = criarELogar(Role.CLIENTE);
        EnvelopeCriado env = prepararContratoAceitoComEnvelope(cliente);
        UUID envelopeIdOriginal = envelopeAssinaturaRepository
                .findByContratoId(env.contratoId())
                .orElseThrow()
                .getId();

        Autenticado financeiro = criarELogar(Role.FINANCEIRO);
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/contratos/" + env.contratoId() + "/assinar")
                .then()
                .statusCode(202);

        // Mesmo envelope (mesmo id); apenas 1 linha
        assertThat(envelopeAssinaturaRepository
                        .findByContratoId(env.contratoId())
                        .orElseThrow()
                        .getId())
                .isEqualTo(envelopeIdOriginal);
        assertThat(envelopeAssinaturaRepository.findAll()).hasSize(1);
    }
}
