package com.dynamis.sep_api.cobranca.web;

import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
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
 * E2E backend do historico owner-scoped de recebimentos do tomador (Sprint 23). Sobe Spring Boot
 * completo em RANDOM_PORT + Postgres local ({@code sep_test}) e exercita seguranca real (JWT +
 * @PreAuthorize), provando ownership, nao-enumeracao, minimizacao do JSON e isolamento do endpoint
 * operacional interno.
 *
 * <p>Recebimentos sao semeados direto via repository (cascade da agenda) para focar na leitura, sem
 * acionar o pipeline de POST/escrow/idempotencia — ja coberto por {@link CobrancaIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CobrancaTomadorConsultaIT {

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
    UsuarioRepository usuarioRepository;

    @Autowired
    AuditLogSegurancaRepository auditLogRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    PlatformTransactionManager txManager;

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
            throw new IllegalStateException(
                    "CobrancaTomadorConsultaIT deve rodar apenas no banco sep_test; URL: " + url);
        }
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

    private static final String[] CPFS = {"52998224725", "11144477735", "87748248800", "39053344705", "12345678909"};
    private final AtomicInteger cpfCursor = new AtomicInteger();

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

    private UUID criarParcelaDe(Autenticado tomador) {
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
        return parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(contrato.getId())
                .get(0)
                .getId();
    }

    private UUID seedRecebimento(UUID parcelaId, String valor, OffsetDateTime data, String key) {
        // Transacao explicita: registrarRecebimento inicializa a colecao lazy 'recebimentos' e
        // precisa de sessao aberta (o @SpringBootTest nao envolve o metodo de teste em transacao).
        TransactionTemplate tx = new TransactionTemplate(txManager);
        return tx.execute(status -> {
            ParcelaCobranca p = parcelaRepository.findById(parcelaId).orElseThrow();
            var recebimento = p.registrarRecebimento(
                    new BigDecimal(valor), p.valorTotal(), data, "PIX", null, key, null, UUID.randomUUID());
            parcelaRepository.saveAndFlush(p);
            return recebimento.getId();
        });
    }

    private static void pollUntil(Supplier<Boolean> cond, String desc) {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(cond.get())) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            }
        }
        throw new AssertionError("Timeout aguardando: " + desc);
    }

    private static final String PATH = "/api/v1/cobranca/parcelas/{parcelaId}/recebimentos";

    // ============== cenarios ==============

    @Test
    void ownerComDoisRecebimentos_retorna200OrdemDesc() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        UUID parcelaId = criarParcelaDe(tomador);
        UUID idAntigo = seedRecebimento(parcelaId, "100.00", OffsetDateTime.parse("2026-06-10T10:00:00-03:00"), "k1");
        UUID idRecente = seedRecebimento(parcelaId, "200.00", OffsetDateTime.parse("2026-06-20T10:00:00-03:00"), "k2");

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(PATH, parcelaId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].recebimentoId", equalTo(idRecente.toString()))
                .body("[1].recebimentoId", equalTo(idAntigo.toString()));
    }

    @Test
    void ownerSemRecebimentos_retorna200Vazio() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        UUID parcelaId = criarParcelaDe(tomador);

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(PATH, parcelaId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    void outroClienteParaParcelaExistente_403() {
        Autenticado dono = criarELogar(Role.CLIENTE);
        Autenticado alheio = criarELogar(Role.CLIENTE);
        UUID parcelaId = criarParcelaDe(dono);
        seedRecebimento(parcelaId, "100.00", OffsetDateTime.parse("2026-06-10T10:00:00-03:00"), "k1");

        RestAssured.given()
                .header("Authorization", "Bearer " + alheio.token())
                .when()
                .get(PATH, parcelaId)
                .then()
                .statusCode(403);
    }

    @Test
    void clienteParaParcelaInexistente_403() {
        Autenticado tomador = criarELogar(Role.CLIENTE);

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(PATH, UUID.randomUUID())
                .then()
                .statusCode(403);
    }

    @Test
    void endpointGlobalDeRecebimentos_continua403ParaCliente() {
        Autenticado tomador = criarELogar(Role.CLIENTE);

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get("/api/v1/cobranca/recebimentos")
                .then()
                .statusCode(403);
    }

    @Test
    void financeiroNoEndpointNovo_403() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO);
        UUID parcelaId = criarParcelaDe(tomador);

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get(PATH, parcelaId)
                .then()
                .statusCode(403);
    }

    @Test
    void adminNoEndpointNovo_403() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado admin = criarELogar(Role.ADMIN);
        UUID parcelaId = criarParcelaDe(tomador);

        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .get(PATH, parcelaId)
                .then()
                .statusCode(403);
    }

    @Test
    void respostaContemApenasCamposPublicos() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        UUID parcelaId = criarParcelaDe(tomador);
        seedRecebimento(parcelaId, "150.00", OffsetDateTime.parse("2026-06-15T10:00:00-03:00"), "k1");

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(PATH, parcelaId)
                .then()
                .statusCode(200)
                .body("[0].size()", equalTo(4))
                .body("[0]", hasKey("recebimentoId"))
                .body("[0]", hasKey("valorRecebido"))
                .body("[0]", hasKey("dataRecebimento"))
                .body("[0]", hasKey("meioPagamento"))
                .body("[0]", not(hasKey("movimentacaoEscrowId")))
                .body("[0]", not(hasKey("identificadorExterno")))
                .body("[0]", not(hasKey("idempotencyKey")))
                .body("[0]", not(hasKey("observacao")))
                .body("[0]", not(hasKey("registradoPor")))
                .body("[0]", not(hasKey("novo")))
                .body("[0]", not(hasKey("statusParcela")));
    }
}
