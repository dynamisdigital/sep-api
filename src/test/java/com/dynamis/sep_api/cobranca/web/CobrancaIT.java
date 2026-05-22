package com.dynamis.sep_api.cobranca.web;

import com.dynamis.sep_api.cobranca.application.job.MarcarParcelaAtrasadaJob;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
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
import com.dynamis.sep_api.escrow.infrastructure.persistence.ContaEscrowRepository;
import com.dynamis.sep_api.escrow.infrastructure.persistence.MovimentacaoEscrowRepository;
import com.dynamis.sep_api.escrow.infrastructure.persistence.WalletRepository;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E backend do ciclo completo de cobranca (Sprint 12 Task 12.8). Sobe Spring Boot completo em
 * RANDOM_PORT + Postgres local (profile {@code test}, banco {@code sep_test}).
 *
 * <p>Reativa via {@link DynamicPropertySource} o {@code app.cobranca.auto-geracao-pos-assinatura}
 * (desligado em {@code application-test.yml} pra ITs alheios). O {@code scheduling-habilitado}
 * fica desligado pra evitar disparo aleatorio do cron; o teste de atraso invoca
 * {@link MarcarParcelaAtrasadaJob#executar()} diretamente.
 *
 * <p>Setup direto de contrato ASSINADO via repositories (evita replicar pipeline completo
 * proposta+contrato+aceite+webhook). O fluxo do listener real eh validado disparando
 * {@link ContratoAssinadoEvent} dentro de uma {@code @Transactional} via
 * {@link TransactionTemplate} — o {@code @TransactionalEventListener(AFTER_COMMIT)} dispara
 * a geracao da agenda como em producao.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CobrancaIT {

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
    ContaEscrowRepository contaEscrowRepository;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    MovimentacaoEscrowRepository movimentacaoEscrowRepository;

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
    MarcarParcelaAtrasadaJob marcarParcelaAtrasadaJob;

    @Autowired
    JdbcTemplate jdbcTemplate;

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
            throw new IllegalStateException("CobrancaIT deve rodar apenas no banco sep_test; URL atual: " + url);
        }
        // Ordem FK-safe: filhos antes dos pais. Escrow tree separada da cobranca tree
        // (recebimento->parcela->agenda->contrato; movimentacao->wallet->conta).
        recebimentoRepository.deleteAll();
        movimentacaoEscrowRepository.deleteAll();
        walletRepository.deleteAll();
        contaEscrowRepository.deleteAll();
        agendaRepository.deleteAll();
        contratoRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        auditLogRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // ============== fixtures ==============

    private record Autenticado(UUID id, String email, String token) {}

    private static final String[] CPFS = {"52998224725", "11144477735", "87748248800", "39053344705", "12345678909"};
    private final AtomicInteger cpfCursor = new AtomicInteger();

    private Autenticado criarELogar(Role role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = role.name().toLowerCase() + "-" + suffix + "@sep.test";
        String senha = "senha-passphrase-segura";
        Usuario u = Usuario.criar(email, passwordEncoder.encode(senha), role);
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

    private record ContratoAssinadoFixture(UUID contratoId, UUID propostaId, UUID tomadorId) {}

    private ContratoAssinadoFixture criarContratoAssinado(Autenticado tomador) {
        String cpf = CPFS[cpfCursor.getAndIncrement() % CPFS.length];
        SolicitacaoOnboarding s =
                SolicitacaoOnboarding.criarPessoa(tomador.id(), new Cpf(cpf), "Tomador", LocalDate.of(1990, 1, 1));
        UUID onbId = onboardingRepository.saveAndFlush(s).getId();
        PropostaCredito p =
                PropostaCredito.criar(tomador.id(), onbId, TipoOperacao.CAPITAL_GIRO, Money.brl("12000"), 12);
        propostaRepository.saveAndFlush(p);
        Contrato contrato = Contrato.criar(p.getId(), tomador.id(), TipoContrato.MUTUO);
        contrato.adicionarVersao("conteudo", "0".repeat(64));
        contrato.marcarAceito();
        contrato.marcarEmAssinatura();
        contrato.marcarAssinado();
        contratoRepository.saveAndFlush(contrato);
        return new ContratoAssinadoFixture(contrato.getId(), p.getId(), tomador.id());
    }

    private void dispararContratoAssinadoEvent(ContratoAssinadoFixture fixture) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.execute(status -> {
            eventPublisher.publishEvent(new ContratoAssinadoEvent(
                    fixture.contratoId(),
                    fixture.propostaId(),
                    fixture.tomadorId(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "fake",
                    "env-" + fixture.contratoId(),
                    "abcdef",
                    OffsetDateTime.now()));
            return null;
        });
    }

    private static void pollUntil(Supplier<Boolean> cond, String desc) {
        // 10s deadline (era 5s) — listeners encadeados @TransactionalEventListener(AFTER_COMMIT)
        // + REQUIRES_NEW podem demorar em CI sob carga.
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

    // ============== cenarios obrigatorios ==============

    @Test
    void contratoAssinado_disparaListener_eCriaAgenda12Parcelas() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);

        dispararContratoAssinadoEvent(fixture);

        pollUntil(() -> agendaRepository.existsByContratoId(fixture.contratoId()), "agenda criada");
        AgendaPagamento agenda =
                agendaRepository.findByContratoId(fixture.contratoId()).orElseThrow();
        assertThat(agenda.getNumeroParcelas()).isEqualTo(12);
        assertThat(parcelaRepository.findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId()))
                .hasSize(12);
    }

    @Test
    void tomadorOwner_consultaAgenda200() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        dispararContratoAssinadoEvent(fixture);
        pollUntil(() -> agendaRepository.existsByContratoId(fixture.contratoId()), "agenda criada");

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get("/api/v1/cobranca/contratos/" + fixture.contratoId() + "/agenda")
                .then()
                .statusCode(200)
                .body("numeroParcelas", org.hamcrest.Matchers.equalTo(12));
    }

    @Test
    void clienteAlheio_consultaAgenda403() {
        Autenticado dono = criarELogar(Role.CLIENTE);
        Autenticado alheio = criarELogar(Role.CLIENTE);
        ContratoAssinadoFixture fixture = criarContratoAssinado(dono);
        dispararContratoAssinadoEvent(fixture);
        pollUntil(() -> agendaRepository.existsByContratoId(fixture.contratoId()), "agenda criada");

        RestAssured.given()
                .header("Authorization", "Bearer " + alheio.token())
                .when()
                .get("/api/v1/cobranca/contratos/" + fixture.contratoId() + "/agenda")
                .then()
                .statusCode(403);
    }

    @Test
    void financeiroRegistraRecebimentoTotal_parcelaVaiPagaEEscrowCriado() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        dispararContratoAssinadoEvent(fixture);
        pollUntil(() -> agendaRepository.existsByContratoId(fixture.contratoId()), "agenda criada");
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();
        BigDecimal valorParcela =
                parcelaRepository.findById(parcelaId).orElseThrow().valorTotal();

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("Idempotency-Key", "key-total-1")
                .contentType(ContentType.JSON)
                .body("{\"valorRecebido\":" + valorParcela.toPlainString()
                        + ",\"dataRecebimento\":\"2026-05-22T10:00:00-03:00\","
                        + "\"meioPagamento\":\"TRANSFERENCIA\"}")
                .when()
                .post("/api/v1/cobranca/parcelas/" + parcelaId + "/recebimentos")
                .then()
                .statusCode(200)
                .body("statusParcela", org.hamcrest.Matchers.equalTo("PAGA"))
                .body("novo", org.hamcrest.Matchers.equalTo(true));

        assertThat(parcelaRepository.findById(parcelaId).orElseThrow().getStatus())
                .isEqualTo(StatusParcela.PAGA);
        assertThat(movimentacaoEscrowRepository.findByIdempotencyKey("key-total-1"))
                .isPresent();
    }

    @Test
    void financeiroRegistraRecebimentoParcial_parcelaVaiParcialmentePaga() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        dispararContratoAssinadoEvent(fixture);
        pollUntil(() -> agendaRepository.existsByContratoId(fixture.contratoId()), "agenda criada");
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("Idempotency-Key", "key-parcial-1")
                .contentType(ContentType.JSON)
                .body("{\"valorRecebido\":100.00,\"dataRecebimento\":\"2026-05-22T10:00:00-03:00\","
                        + "\"meioPagamento\":\"TRANSFERENCIA\"}")
                .when()
                .post("/api/v1/cobranca/parcelas/" + parcelaId + "/recebimentos")
                .then()
                .statusCode(200)
                .body("statusParcela", org.hamcrest.Matchers.equalTo("PARCIALMENTE_PAGA"));
    }

    @Test
    void clienteTentaRegistrarRecebimento403() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        dispararContratoAssinadoEvent(fixture);
        pollUntil(() -> agendaRepository.existsByContratoId(fixture.contratoId()), "agenda criada");
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .header("Idempotency-Key", "key-cli-1")
                .contentType(ContentType.JSON)
                .body("{\"valorRecebido\":100.00,\"dataRecebimento\":\"2026-05-22T10:00:00-03:00\","
                        + "\"meioPagamento\":\"TRANSFERENCIA\"}")
                .when()
                .post("/api/v1/cobranca/parcelas/" + parcelaId + "/recebimentos")
                .then()
                .statusCode(403);
    }

    @Test
    void idempotencyKeyRepetida_naoDuplicaRecebimentoNemEscrow() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        dispararContratoAssinadoEvent(fixture);
        pollUntil(() -> agendaRepository.existsByContratoId(fixture.contratoId()), "agenda criada");
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();
        String body = "{\"valorRecebido\":100.00,\"dataRecebimento\":\"2026-05-22T10:00:00-03:00\","
                + "\"meioPagamento\":\"TRANSFERENCIA\"}";

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("Idempotency-Key", "key-dup")
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/cobranca/parcelas/" + parcelaId + "/recebimentos")
                .then()
                .statusCode(200)
                .body("novo", org.hamcrest.Matchers.equalTo(true));

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("Idempotency-Key", "key-dup")
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/cobranca/parcelas/" + parcelaId + "/recebimentos")
                .then()
                .statusCode(200)
                .body("novo", org.hamcrest.Matchers.equalTo(false));

        assertThat(recebimentoRepository.findAll()).hasSize(1);
        assertThat(movimentacaoEscrowRepository.findAll()).hasSize(1);
    }

    @Test
    void novaKeyEmParcelaJaPaga_retorna409() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        dispararContratoAssinadoEvent(fixture);
        pollUntil(() -> agendaRepository.existsByContratoId(fixture.contratoId()), "agenda criada");
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();
        BigDecimal valorParcela =
                parcelaRepository.findById(parcelaId).orElseThrow().valorTotal();

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("Idempotency-Key", "key-paga")
                .contentType(ContentType.JSON)
                .body("{\"valorRecebido\":" + valorParcela.toPlainString()
                        + ",\"dataRecebimento\":\"2026-05-22T10:00:00-03:00\","
                        + "\"meioPagamento\":\"TRANSFERENCIA\"}")
                .when()
                .post("/api/v1/cobranca/parcelas/" + parcelaId + "/recebimentos")
                .then()
                .statusCode(200);

        // Tenta segundo recebimento com nova key — parcela ja PAGA -> 409
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("Idempotency-Key", "key-segunda")
                .contentType(ContentType.JSON)
                .body("{\"valorRecebido\":10.00,\"dataRecebimento\":\"2026-05-22T11:00:00-03:00\","
                        + "\"meioPagamento\":\"TRANSFERENCIA\"}")
                .when()
                .post("/api/v1/cobranca/parcelas/" + parcelaId + "/recebimentos")
                .then()
                .statusCode(409);
    }

    @Test
    void jobMarcaParcelaVencidaAtrasada_eValorAtualizadoIncluiMoraEMulta() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        dispararContratoAssinadoEvent(fixture);
        pollUntil(() -> agendaRepository.existsByContratoId(fixture.contratoId()), "agenda criada");
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();

        // Forca data vencimento no passado via SQL direto — a coluna eh updatable=false na entidade
        // pra preservar imutabilidade da agenda em producao, mas no IT precisamos simular atraso.
        jdbcTemplate.update(
                "UPDATE parcela_cobranca SET data_vencimento = ? WHERE id = ?",
                java.sql.Date.valueOf(LocalDate.now().minusDays(30)),
                parcelaId);

        int marcadas = marcarParcelaAtrasadaJob.executar();
        assertThat(marcadas).isGreaterThanOrEqualTo(1);
        assertThat(parcelaRepository.findById(parcelaId).orElseThrow().getStatus())
                .isEqualTo(StatusParcela.ATRASADA);

        // GET /parcelas/{id} retorna valor atualizado com juros mora + multa > 0
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get("/api/v1/cobranca/parcelas/" + parcelaId)
                .then()
                .statusCode(200)
                .body("jurosMora", org.hamcrest.Matchers.not(org.hamcrest.Matchers.equalTo(0.0f)))
                .body("multa", org.hamcrest.Matchers.not(org.hamcrest.Matchers.equalTo(0.0f)));
    }

    @Test
    void auditLog_registraAGENDA_GERADA_e_PARCELA_PAGA_apos_recebimento_total() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        dispararContratoAssinadoEvent(fixture);
        pollUntil(() -> agendaRepository.existsByContratoId(fixture.contratoId()), "agenda criada");
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();
        BigDecimal valor = parcelaRepository.findById(parcelaId).orElseThrow().valorTotal();

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("Idempotency-Key", "key-audit-1")
                .contentType(ContentType.JSON)
                .body("{\"valorRecebido\":" + valor.toPlainString()
                        + ",\"dataRecebimento\":\"2026-05-22T10:00:00-03:00\","
                        + "\"meioPagamento\":\"TRANSFERENCIA\"}")
                .when()
                .post("/api/v1/cobranca/parcelas/" + parcelaId + "/recebimentos")
                .then()
                .statusCode(200);

        // Unico pollUntil cobre os 4 tipos em paralelo (era 4x 5s sequenciais = ate 20s worst).
        pollUntil(
                () -> {
                    var tipos = auditLogRepository.findAll().stream()
                            .map(a -> a.getTipo())
                            .collect(java.util.stream.Collectors.toSet());
                    return tipos.contains(TipoEventoSeguranca.AGENDA_GERADA)
                            && tipos.contains(TipoEventoSeguranca.RECEBIMENTO_REGISTRADO)
                            && tipos.contains(TipoEventoSeguranca.PARCELA_PAGA)
                            && tipos.contains(TipoEventoSeguranca.MOVIMENTACAO_ESCROW_CRIADA);
                },
                "audit log com AGENDA_GERADA + RECEBIMENTO_REGISTRADO + PARCELA_PAGA + MOVIMENTACAO_ESCROW_CRIADA");
    }
}
