package com.dynamis.sep_api.pix.web;

import com.dynamis.sep_api.cobranca.infrastructure.persistence.AgendaPagamentoRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RecebimentoRepository;
import com.dynamis.sep_api.contratos.domain.event.ContratoAssinadoEvent;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixReferenciaRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
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
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

/**
 * E2E backend das leituras Pix owner-scoped do tomador (Sprint 26 — Gates P1 e P2). Sobe Spring Boot
 * completo em RANDOM_PORT + Postgres local ({@code sep_test}) e exercita seguranca real (JWT +
 * @PreAuthorize), provando ownership, 404 neutro anti-enumeracao, minimizacao do JSON, ordenacao sem
 * filtro (P1), pareamento referencia/recebimento por {@code referenciaId} (P2) e isolamento dos
 * endpoints operacionais internos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PixTomadorLeituraIT {

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
        registry.add("app.cobranca.auto-geracao-pos-assinatura", () -> "true");
    }

    @LocalServerPort
    int port;

    @Autowired
    AgendaPagamentoRepository agendaRepository;

    @Autowired
    ParcelaCobrancaRepository parcelaRepository;

    @Autowired
    RecebimentoRepository recebimentoRepository;

    @Autowired
    ContratoRepository contratoRepository;

    @Autowired
    PropostaCreditoRepository propostaRepository;

    @Autowired
    SolicitacaoOnboardingRepository onboardingRepository;

    @Autowired
    PixTransferenciaRepository transferenciaRepository;

    @Autowired
    PixReferenciaRecebimentoRepository referenciaRepository;

    @Autowired
    PixRecebimentoRepository pixRecebimentoRepository;

    @Autowired
    AuditLogSegurancaRepository auditLogRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    PlatformTransactionManager txManager;

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
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.contains("sep_test")) {
            throw new IllegalStateException("PixTomadorLeituraIT deve rodar apenas no banco sep_test; URL: " + url);
        }
        pixRecebimentoRepository.deleteAll();
        referenciaRepository.deleteAll();
        transferenciaRepository.deleteAll();
        recebimentoRepository.deleteAll();
        agendaRepository.deleteAll();
        contratoRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        auditLogRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // ============== fixtures ==============

    private record Autenticado(UUID id, String token) {}

    private record Contexto(UUID contratoId, UUID propostaId, UUID parcelaId) {}

    private static final String[] CPFS = {"52998224725", "11144477735", "87748248800", "39053344705", "12345678909"};
    private final AtomicInteger cpfCursor = new AtomicInteger();
    private final AtomicInteger keyCursor = new AtomicInteger();

    private Autenticado criarELogar(Role role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = role.name().toLowerCase() + "-" + suffix + "@sep.test";
        String senha = "senha-passphrase-segura";
        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar(email, passwordEncoder.encode(senha), role));
        String token = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + senha + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        return new Autenticado(u.getId(), token);
    }

    private Contexto criarContratoComParcela(Autenticado tomador) {
        String cpf = CPFS[cpfCursor.getAndIncrement() % CPFS.length];
        UUID onbId = onboardingRepository
                .saveAndFlush(SolicitacaoOnboarding.criarPessoa(
                        tomador.id(), new Cpf(cpf), "Tomador", LocalDate.of(1990, 1, 1)))
                .getId();
        PropostaCredito p =
                PropostaCredito.criar(tomador.id(), onbId, TipoOperacao.CAPITAL_GIRO, Money.brl("12000"), 12);
        propostaRepository.saveAndFlush(p);
        Contrato contrato = Contrato.criar(p.getId(), tomador.id(), TipoContrato.MUTUO);
        contrato.adicionarVersao("conteudo", "0".repeat(64));
        contrato.marcarAceito();
        contrato.marcarEmAssinatura();
        contrato.marcarAssinado();
        contratoRepository.saveAndFlush(contrato);

        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.execute(status -> {
            eventPublisher.publishEvent(new ContratoAssinadoEvent(
                    contrato.getId(),
                    p.getId(),
                    tomador.id(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "fake",
                    "env-" + contrato.getId(),
                    "abcdef",
                    OffsetDateTime.now()));
            return null;
        });
        pollUntil(() -> agendaRepository.existsByContratoIdAndAtivaTrue(contrato.getId()), "agenda criada");
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(contrato.getId())
                .get(0)
                .getId();
        return new Contexto(contrato.getId(), p.getId(), parcelaId);
    }

    private void seedTransferencia(Contexto ctx, UUID tomadorId, StatusPixTransferencia status, String valor) {
        String key = "idem-" + keyCursor.incrementAndGet();
        PixTransferencia t = PixTransferencia.criarDesembolso(
                ctx.contratoId(),
                ctx.propostaId(),
                tomadorId,
                new BigDecimal(valor),
                "a".repeat(64),
                "mascara",
                key,
                "corr");
        switch (status) {
            case CRIADA -> {}
            case SOLICITADA -> t.marcarSolicitada("ext-" + key);
            case PROCESSANDO -> {
                t.marcarSolicitada("ext-" + key);
                t.marcarProcessando();
            }
            case CONCLUIDA -> {
                t.marcarSolicitada("ext-" + key);
                t.marcarConcluida();
            }
            case FALHOU -> t.marcarFalhou();
            case CANCELADA -> t.cancelar();
        }
        transferenciaRepository.saveAndFlush(t);
    }

    private UUID seedReferencia(Contexto ctx, UUID tomadorId, StatusPixReferenciaRecebimento status) {
        String txid = "txid-" + keyCursor.incrementAndGet();
        PixReferenciaRecebimento ref = PixReferenciaRecebimento.criar(
                ctx.parcelaId(), ctx.contratoId(), tomadorId, new BigDecimal("350.00"), txid, "corr");
        switch (status) {
            case ATIVA -> {}
            case PAGA -> ref.marcarPaga();
            case DIVERGENTE -> ref.marcarDivergente();
            case EXPIRADA -> ref.marcarExpirada();
            case CANCELADA -> ref.cancelar();
        }
        return referenciaRepository.saveAndFlush(ref).getId();
    }

    private void seedRecebimentoNaoIdentificado(UUID referenciaId, UUID parcelaId) {
        PixRecebimento rec = PixRecebimento.registrar(
                "e2e-" + keyCursor.incrementAndGet(), new BigDecimal("350.00"), OffsetDateTime.now(), "corr");
        rec.registrarDivergencia(referenciaId, parcelaId, "valor divergente");
        pixRecebimentoRepository.saveAndFlush(rec);
    }

    private static void pollUntil(Supplier<Boolean> cond, String desc) {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(cond.get())) {
                return;
            }
            dormir(100);
        }
        throw new AssertionError("Timeout aguardando: " + desc);
    }

    private static void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        }
    }

    private static final String P1 = "/api/v1/pix/contratos/{contratoId}/desembolso";
    private static final String P2 = "/api/v1/pix/parcelas/{parcelaId}/status";

    // ============== P1 — desembolso do tomador ==============

    @Test
    void p1_ownerConcluido_200LiquidadoMinimo() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Contexto ctx = criarContratoComParcela(tomador);
        seedTransferencia(ctx, tomador.id(), StatusPixTransferencia.CONCLUIDA, "1500.00");

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(P1, ctx.contratoId())
                .then()
                .statusCode(200)
                .body("status", equalTo("LIQUIDADO"))
                .body("valor", equalTo(1500.00f))
                .body("$", hasKey("atualizadoEm"))
                .body("$", not(hasKey("chaveDestinoMascara")))
                .body("$", not(hasKey("txid")))
                .body("$", not(hasKey("externalId")))
                .body("$", not(hasKey("contratoId")))
                .body("$", not(hasKey("tomadorId")))
                .body("$", not(hasKey("correlationId")));
    }

    @Test
    void p1_desembolsoFalhou_visivel() {
        // O finder sem filtro de status retorna FALHOU (o finder operacional filtrado nao retornaria).
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Contexto ctx = criarContratoComParcela(tomador);
        seedTransferencia(ctx, tomador.id(), StatusPixTransferencia.FALHOU, "900.00");

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(P1, ctx.contratoId())
                .then()
                .statusCode(200)
                .body("status", equalTo("FALHOU"));
    }

    @Test
    void p1_multiplasTransferencias_maisRecenteVence() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Contexto ctx = criarContratoComParcela(tomador);
        seedTransferencia(ctx, tomador.id(), StatusPixTransferencia.CONCLUIDA, "1000.00");
        dormir(15);
        seedTransferencia(ctx, tomador.id(), StatusPixTransferencia.FALHOU, "1000.00");

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(P1, ctx.contratoId())
                .then()
                .statusCode(200)
                .body("status", equalTo("FALHOU"));
    }

    @Test
    void p1_contratoAlheioEInexistente_404Neutro() {
        Autenticado dono = criarELogar(Role.CLIENTE);
        Autenticado alheio = criarELogar(Role.CLIENTE);
        Contexto ctx = criarContratoComParcela(dono);
        seedTransferencia(ctx, dono.id(), StatusPixTransferencia.CONCLUIDA, "1500.00");

        RestAssured.given()
                .header("Authorization", "Bearer " + alheio.token())
                .when()
                .get(P1, ctx.contratoId())
                .then()
                .statusCode(404);

        RestAssured.given()
                .header("Authorization", "Bearer " + dono.token())
                .when()
                .get(P1, UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void p1_contratoProprioSemDesembolso_404() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Contexto ctx = criarContratoComParcela(tomador);

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(P1, ctx.contratoId())
                .then()
                .statusCode(404);
    }

    @Test
    void p1_financeiroEAdmin_403() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Contexto ctx = criarContratoComParcela(tomador);
        seedTransferencia(ctx, tomador.id(), StatusPixTransferencia.CONCLUIDA, "1500.00");
        Autenticado financeiro = criarELogar(Role.FINANCEIRO);
        Autenticado admin = criarELogar(Role.ADMIN);

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get(P1, ctx.contratoId())
                .then()
                .statusCode(403);
        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .get(P1, ctx.contratoId())
                .then()
                .statusCode(403);
    }

    @Test
    void endpointsOperacionaisContinuamRestritosParaCliente() {
        Autenticado tomador = criarELogar(Role.CLIENTE);

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get("/api/v1/pix/desembolsos/" + UUID.randomUUID())
                .then()
                .statusCode(403);
        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get("/api/v1/pix/recebimentos/" + UUID.randomUUID())
                .then()
                .statusCode(403);
    }

    // ============== P2 — status Pix da parcela ==============

    @Test
    void p2_referenciaAtiva_200Aguardando() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Contexto ctx = criarContratoComParcela(tomador);
        seedReferencia(ctx, tomador.id(), StatusPixReferenciaRecebimento.ATIVA);

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(P2, ctx.parcelaId())
                .then()
                .statusCode(200)
                .body("status", equalTo("AGUARDANDO"))
                .body("$", hasKey("mensagemPublica"))
                .body("$", not(hasKey("txid")))
                .body("$", not(hasKey("codigoCopiaCola")))
                .body("$", not(hasKey("endToEndId")))
                .body("$", not(hasKey("motivoDivergencia")))
                .body("$", not(hasKey("referenciaId")));
    }

    @Test
    void p2_referenciaPaga_200Liquidado() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Contexto ctx = criarContratoComParcela(tomador);
        seedReferencia(ctx, tomador.id(), StatusPixReferenciaRecebimento.PAGA);

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(P2, ctx.parcelaId())
                .then()
                .statusCode(200)
                .body("status", equalTo("LIQUIDADO"));
    }

    @Test
    void p2_referenciaNovaNaoCasaComRecebimentoDeReferenciaAntiga() {
        // Referencia antiga com recebimento NAO_IDENTIFICADO; referencia nova ATIVA sem recebimento.
        // Como o recebimento e buscado pela referencia atual, o estado deve ser AGUARDANDO (nao DIVERGENTE).
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Contexto ctx = criarContratoComParcela(tomador);
        // Referencia antiga em estado terminal (EXPIRADA): a unica ATIVA permitida por parcela sera a nova.
        UUID referenciaAntiga = seedReferencia(ctx, tomador.id(), StatusPixReferenciaRecebimento.EXPIRADA);
        seedRecebimentoNaoIdentificado(referenciaAntiga, ctx.parcelaId());
        dormir(15);
        seedReferencia(ctx, tomador.id(), StatusPixReferenciaRecebimento.ATIVA);

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(P2, ctx.parcelaId())
                .then()
                .statusCode(200)
                .body("status", equalTo("AGUARDANDO"));
    }

    @Test
    void p2_parcelaAlheiaEInexistente_404Neutro() {
        Autenticado dono = criarELogar(Role.CLIENTE);
        Autenticado alheio = criarELogar(Role.CLIENTE);
        Contexto ctx = criarContratoComParcela(dono);
        seedReferencia(ctx, dono.id(), StatusPixReferenciaRecebimento.ATIVA);

        RestAssured.given()
                .header("Authorization", "Bearer " + alheio.token())
                .when()
                .get(P2, ctx.parcelaId())
                .then()
                .statusCode(404);
        RestAssured.given()
                .header("Authorization", "Bearer " + dono.token())
                .when()
                .get(P2, UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void p2_parcelaProprioSemPix_404() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Contexto ctx = criarContratoComParcela(tomador);

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(P2, ctx.parcelaId())
                .then()
                .statusCode(404);
    }

    @Test
    void p2_financeiro_403() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Contexto ctx = criarContratoComParcela(tomador);
        seedReferencia(ctx, tomador.id(), StatusPixReferenciaRecebimento.ATIVA);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO);

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get(P2, ctx.parcelaId())
                .then()
                .statusCode(403);
    }
}
