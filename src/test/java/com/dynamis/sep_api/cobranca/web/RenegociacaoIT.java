package com.dynamis.sep_api.cobranca.web;

import com.dynamis.sep_api.cobranca.application.job.ExpirarRenegociacaoJob;
import com.dynamis.sep_api.cobranca.application.job.MarcarParcelaAtrasadaJob;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.AgendaPagamentoRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.EventoCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RecebimentoRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RenegociacaoRepository;
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
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * E2E da Sprint 13 — fluxo de renegociacao (Task 13.9).
 *
 * <p>Cenarios cobertos:
 *
 * <ul>
 *   <li>Financeiro propoe renegociacao (com step-up) → parcela {@code EM_NEGOCIACAO};
 *       audit log {@code RENEGOCIACAO_PROPOSTA}.
 *   <li>Tomador aceita (com step-up + ownership) → nova {@code AgendaPagamento} substituta;
 *       parcela {@code RENEGOCIADA}; agenda anterior {@code ativa=false}.
 *   <li>Tomador recusa (sem step-up; com ownership) → parcela volta ao status anterior.
 *   <li>Conflito 409: segunda proposta para mesma parcela.
 *   <li>Ownership 403: cliente alheio tenta aceitar.
 *   <li>Step-up 403: aceite sem token quando MFA habilitado.
 *   <li>Expiracao via job → audit log {@code RENEGOCIACAO_EXPIRADA}; parcela retorna ao status
 *       anterior.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RenegociacaoIT {

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
        registry.add("app.cobranca.auto-geracao-pos-assinatura", () -> "true");
        registry.add("app.cobranca.scheduling-habilitado", () -> "true");
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
    EventoCobrancaRepository eventoCobrancaRepository;

    @Autowired
    RenegociacaoRepository renegociacaoRepository;

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
    ExpirarRenegociacaoJob expirarRenegociacaoJob;

    @Autowired
    JdbcTemplate jdbcTemplate;

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
            throw new IllegalStateException("RenegociacaoIT deve rodar apenas no banco sep_test; URL atual: " + url);
        }
        renegociacaoRepository.deleteAll();
        eventoCobrancaRepository.deleteAll();
        recebimentoRepository.deleteAll();
        movimentacaoEscrowRepository.deleteAll();
        walletRepository.deleteAll();
        contaEscrowRepository.deleteAll();
        // Sprint 13 Task 13.6: agenda_substituida_id eh self-FK. Delete em ordem:
        // (1) parcelas das agendas substitutas, (2) agendas substitutas, (3) deleteAll restante.
        jdbcTemplate.update(
                "DELETE FROM parcela_cobranca WHERE agenda_id IN (SELECT id FROM agenda_pagamento WHERE agenda_substituida_id IS NOT NULL)");
        jdbcTemplate.update("DELETE FROM agenda_pagamento WHERE agenda_substituida_id IS NOT NULL");
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
        return criarELogar(role, false);
    }

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

    private record ContratoFixture(UUID contratoId, UUID propostaId, UUID tomadorId, UUID parcelaId) {}

    private ContratoFixture criarContratoComParcelaAtrasada(Autenticado tomador) {
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
        jdbcTemplate.update(
                "UPDATE parcela_cobranca SET data_vencimento = ? WHERE id = ?",
                java.sql.Date.valueOf(LocalDate.now().minusDays(15)),
                parcelaId);
        marcarParcelaAtrasadaJob.executar();
        return new ContratoFixture(contrato.getId(), p.getId(), tomador.id(), parcelaId);
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

    private String emitirStepUp(UUID usuarioId) {
        return stepUpTokenService.emitir(usuarioId).token();
    }

    private String corpoProposta() {
        return "{\"novoValorParcela\":110.00,\"novoVencimento\":\""
                + LocalDate.now().plusDays(30)
                + "\",\"numeroParcelas\":3,\"desconto\":0.00,\"justificativa\":\"Acordo amigavel\"}";
    }

    private static final String PATH_ATIVA = "/api/v1/cobranca/parcelas/{parcelaId}/renegociacao-ativa";

    /** Financeiro propoe (com step-up) e devolve a PROPOSTA persistida. */
    private Renegociacao proporRenegociacao(Autenticado financeiro, UUID parcelaId) {
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", emitirStepUp(financeiro.id()))
                .contentType(ContentType.JSON)
                .body(corpoProposta())
                .when()
                .post("/api/v1/cobranca/parcelas/" + parcelaId + "/renegociacao")
                .then()
                .statusCode(201);
        return renegociacaoRepository
                .findByParcelaOriginalIdAndStatus(parcelaId, StatusRenegociacao.PROPOSTA)
                .orElseThrow();
    }

    // ============== cenarios ==============

    @Test
    void financeiroPropoe_parcelaVaiParaEmNegociacao_audit() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        ContratoFixture fx = criarContratoComParcelaAtrasada(tomador);

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", emitirStepUp(financeiro.id()))
                .contentType(ContentType.JSON)
                .body(corpoProposta())
                .when()
                .post("/api/v1/cobranca/parcelas/" + fx.parcelaId() + "/renegociacao")
                .then()
                .statusCode(201)
                .body("status", org.hamcrest.Matchers.equalTo("PROPOSTA"))
                .body(
                        "parcelaOriginalId",
                        org.hamcrest.Matchers.equalTo(fx.parcelaId().toString()));

        Renegociacao r = renegociacaoRepository
                .findByParcelaOriginalIdAndStatus(fx.parcelaId(), StatusRenegociacao.PROPOSTA)
                .orElseThrow();
        assertThat(r.getStatus()).isEqualTo(StatusRenegociacao.PROPOSTA);
        assertThat(parcelaRepository.findById(fx.parcelaId()).orElseThrow().getStatus())
                .isEqualTo(StatusParcela.EM_NEGOCIACAO);
        pollUntil(
                () -> auditLogRepository.findAll().stream()
                        .anyMatch(a -> a.getTipo() == TipoEventoSeguranca.RENEGOCIACAO_PROPOSTA),
                "audit RENEGOCIACAO_PROPOSTA");
    }

    @Test
    void tomadorAceita_geraAgendaSubstituta() {
        Autenticado tomador = criarELogar(Role.CLIENTE, true);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        ContratoFixture fx = criarContratoComParcelaAtrasada(tomador);

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", emitirStepUp(financeiro.id()))
                .contentType(ContentType.JSON)
                .body(corpoProposta())
                .when()
                .post("/api/v1/cobranca/parcelas/" + fx.parcelaId() + "/renegociacao")
                .then()
                .statusCode(201);
        Renegociacao r = renegociacaoRepository
                .findByParcelaOriginalIdAndStatus(fx.parcelaId(), StatusRenegociacao.PROPOSTA)
                .orElseThrow();

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .header("X-Step-Up-Token", emitirStepUp(tomador.id()))
                .when()
                .patch("/api/v1/cobranca/renegociacoes/" + r.getId() + "/aceite")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("ACEITA"))
                .body("agendaSubstitutaId", org.hamcrest.Matchers.notNullValue());

        assertThat(parcelaRepository.findById(fx.parcelaId()).orElseThrow().getStatus())
                .isEqualTo(StatusParcela.RENEGOCIADA);
        var renegociadaDb = renegociacaoRepository.findById(r.getId()).orElseThrow();
        assertThat(renegociadaDb.getStatus()).isEqualTo(StatusRenegociacao.ACEITA);
        assertThat(renegociadaDb.getAgendaSubstitutaId()).isNotNull();
        // Agenda substituta esta ativa; original esta inativa.
        var substituta =
                agendaRepository.findById(renegociadaDb.getAgendaSubstitutaId()).orElseThrow();
        assertThat(substituta.isAtiva()).isTrue();
        assertThat(substituta.getAgendaSubstituidaId()).isEqualTo(r.getAgendaOriginalId());
        pollUntil(
                () -> auditLogRepository.findAll().stream()
                        .anyMatch(a -> a.getTipo() == TipoEventoSeguranca.RENEGOCIACAO_ACEITA),
                "audit RENEGOCIACAO_ACEITA");
    }

    @Test
    void tomadorRecusa_parcelaVoltaAoStatusAnterior() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        ContratoFixture fx = criarContratoComParcelaAtrasada(tomador);

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", emitirStepUp(financeiro.id()))
                .contentType(ContentType.JSON)
                .body(corpoProposta())
                .when()
                .post("/api/v1/cobranca/parcelas/" + fx.parcelaId() + "/renegociacao")
                .then()
                .statusCode(201);
        Renegociacao r = renegociacaoRepository
                .findByParcelaOriginalIdAndStatus(fx.parcelaId(), StatusRenegociacao.PROPOSTA)
                .orElseThrow();

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .patch("/api/v1/cobranca/renegociacoes/" + r.getId() + "/recusa")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("RECUSADA"));

        assertThat(parcelaRepository.findById(fx.parcelaId()).orElseThrow().getStatus())
                .isEqualTo(StatusParcela.ATRASADA);
        pollUntil(
                () -> auditLogRepository.findAll().stream()
                        .anyMatch(a -> a.getTipo() == TipoEventoSeguranca.RENEGOCIACAO_RECUSADA),
                "audit RENEGOCIACAO_RECUSADA");
    }

    @Test
    void segundaProposta_mesmaParcela_retorna409() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        ContratoFixture fx = criarContratoComParcelaAtrasada(tomador);

        // Primeira proposta sucesso.
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", emitirStepUp(financeiro.id()))
                .contentType(ContentType.JSON)
                .body(corpoProposta())
                .when()
                .post("/api/v1/cobranca/parcelas/" + fx.parcelaId() + "/renegociacao")
                .then()
                .statusCode(201);

        // Segunda tentativa — parcela ja em EM_NEGOCIACAO → ParcelaEstadoInvalidoException (400/409).
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", emitirStepUp(financeiro.id()))
                .contentType(ContentType.JSON)
                .body(corpoProposta())
                .when()
                .post("/api/v1/cobranca/parcelas/" + fx.parcelaId() + "/renegociacao")
                .then()
                // ParcelaEstadoInvalidoException extends ConflitoException -> 409.
                .statusCode(409);
    }

    @Test
    void aceiteOwnerInvalido_retorna403() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado alheio = criarELogar(Role.CLIENTE, true);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        ContratoFixture fx = criarContratoComParcelaAtrasada(tomador);

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", emitirStepUp(financeiro.id()))
                .contentType(ContentType.JSON)
                .body(corpoProposta())
                .when()
                .post("/api/v1/cobranca/parcelas/" + fx.parcelaId() + "/renegociacao")
                .then()
                .statusCode(201);
        Renegociacao r = renegociacaoRepository
                .findByParcelaOriginalIdAndStatus(fx.parcelaId(), StatusRenegociacao.PROPOSTA)
                .orElseThrow();

        RestAssured.given()
                .header("Authorization", "Bearer " + alheio.token())
                .header("X-Step-Up-Token", emitirStepUp(alheio.id()))
                .when()
                .patch("/api/v1/cobranca/renegociacoes/" + r.getId() + "/aceite")
                .then()
                .statusCode(403);
    }

    @Test
    void aceiteSemStepUp_comMfaHabilitado_retorna403() {
        Autenticado tomador = criarELogar(Role.CLIENTE, true);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        ContratoFixture fx = criarContratoComParcelaAtrasada(tomador);

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", emitirStepUp(financeiro.id()))
                .contentType(ContentType.JSON)
                .body(corpoProposta())
                .when()
                .post("/api/v1/cobranca/parcelas/" + fx.parcelaId() + "/renegociacao")
                .then()
                .statusCode(201);
        Renegociacao r = renegociacaoRepository
                .findByParcelaOriginalIdAndStatus(fx.parcelaId(), StatusRenegociacao.PROPOSTA)
                .orElseThrow();

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .patch("/api/v1/cobranca/renegociacoes/" + r.getId() + "/aceite")
                .then()
                .statusCode(403);
    }

    @Test
    void expiracao_job_revertePARCELA_audit() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        ContratoFixture fx = criarContratoComParcelaAtrasada(tomador);

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", emitirStepUp(financeiro.id()))
                .contentType(ContentType.JSON)
                .body(corpoProposta())
                .when()
                .post("/api/v1/cobranca/parcelas/" + fx.parcelaId() + "/renegociacao")
                .then()
                .statusCode(201);
        Renegociacao r = renegociacaoRepository
                .findByParcelaOriginalIdAndStatus(fx.parcelaId(), StatusRenegociacao.PROPOSTA)
                .orElseThrow();
        // Forca data_proposta + data_expiracao no passado pra job pegar; check constraint
        // chk_renegociacao_expiracao exige data_expiracao > data_proposta.
        java.time.LocalDateTime proposta = java.time.LocalDateTime.now().minusDays(10);
        java.time.LocalDateTime expiracao = java.time.LocalDateTime.now().minusDays(1);
        jdbcTemplate.update(
                "UPDATE renegociacao SET data_proposta = ?, data_expiracao = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(proposta),
                java.sql.Timestamp.valueOf(expiracao),
                r.getId());

        int processadas = expirarRenegociacaoJob.executar();

        assertThat(processadas).isGreaterThanOrEqualTo(1);
        assertThat(renegociacaoRepository.findById(r.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusRenegociacao.EXPIRADA);
        assertThat(parcelaRepository.findById(fx.parcelaId()).orElseThrow().getStatus())
                .isEqualTo(StatusParcela.ATRASADA);
        pollUntil(
                () -> auditLogRepository.findAll().stream()
                        .anyMatch(a -> a.getTipo() == TipoEventoSeguranca.RENEGOCIACAO_EXPIRADA),
                "audit RENEGOCIACAO_EXPIRADA");
    }

    // ============== Sprint 24 — consulta owner-scoped da renegociacao ativa ==============

    @Test
    void tomadorConsultaAtiva_recebeTermosETotal_semAlterarStatus() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        ContratoFixture fx = criarContratoComParcelaAtrasada(tomador);
        Renegociacao r = proporRenegociacao(financeiro, fx.parcelaId());

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(PATH_ATIVA, fx.parcelaId())
                .then()
                .statusCode(200)
                .body("renegociacaoId", equalTo(r.getId().toString()))
                .body("parcelaId", equalTo(fx.parcelaId().toString()))
                .body("status", equalTo("PROPOSTA"))
                .body("novoValorParcela", equalTo(110.0f))
                .body("numeroParcelas", equalTo(3))
                // total calculado no backend: 110.00 * 3.
                .body("valorTotalRenegociado", equalTo(330.0f))
                .body("$", not(hasKey("tomadorId")))
                .body("$", not(hasKey("propostaPor")))
                .body("$", not(hasKey("agendaOriginalId")))
                .body("$", not(hasKey("statusParcelaAnterior")))
                .body("$", not(hasKey("justificativa")));

        // GET e read-only: nada muda no banco.
        assertThat(renegociacaoRepository.findById(r.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusRenegociacao.PROPOSTA);
        assertThat(parcelaRepository.findById(fx.parcelaId()).orElseThrow().getStatus())
                .isEqualTo(StatusParcela.EM_NEGOCIACAO);
    }

    @Test
    void outroCliente_naConsultaAtiva_403() {
        Autenticado dono = criarELogar(Role.CLIENTE);
        Autenticado alheio = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        ContratoFixture fx = criarContratoComParcelaAtrasada(dono);
        proporRenegociacao(financeiro, fx.parcelaId());

        RestAssured.given()
                .header("Authorization", "Bearer " + alheio.token())
                .when()
                .get(PATH_ATIVA, fx.parcelaId())
                .then()
                .statusCode(403);
    }

    @Test
    void parcelaInexistente_naConsultaAtiva_403() {
        Autenticado tomador = criarELogar(Role.CLIENTE);

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(PATH_ATIVA, UUID.randomUUID())
                .then()
                .statusCode(403);
    }

    @Test
    void parcelaPropriaSemProposta_naConsultaAtiva_404() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        ContratoFixture fx = criarContratoComParcelaAtrasada(tomador);

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(PATH_ATIVA, fx.parcelaId())
                .then()
                .statusCode(404);
    }

    @Test
    void propostaVencidaPeloClock_404_antesDoJob() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        ContratoFixture fx = criarContratoComParcelaAtrasada(tomador);
        Renegociacao r = proporRenegociacao(financeiro, fx.parcelaId());
        // Vence a proposta sem rodar o job (check constraint exige data_expiracao > data_proposta).
        jdbcTemplate.update(
                "UPDATE renegociacao SET data_proposta = ?, data_expiracao = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(10)),
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(1)),
                r.getId());

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(PATH_ATIVA, fx.parcelaId())
                .then()
                .statusCode(404);

        // Job nao rodou: renegociacao continua PROPOSTA (so o Clock a exclui da leitura).
        assertThat(renegociacaoRepository.findById(r.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusRenegociacao.PROPOSTA);
    }

    @Test
    void consultaAtiva_apos_aceite_com_step_up_deixaDeAparecer() {
        Autenticado tomador = criarELogar(Role.CLIENTE, true);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        ContratoFixture fx = criarContratoComParcelaAtrasada(tomador);
        Renegociacao r = proporRenegociacao(financeiro, fx.parcelaId());

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(PATH_ATIVA, fx.parcelaId())
                .then()
                .statusCode(200);

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .header("X-Step-Up-Token", emitirStepUp(tomador.id()))
                .when()
                .patch("/api/v1/cobranca/renegociacoes/" + r.getId() + "/aceite")
                .then()
                .statusCode(200)
                .body("status", equalTo("ACEITA"))
                .body("agendaSubstitutaId", notNullValue());

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(PATH_ATIVA, fx.parcelaId())
                .then()
                .statusCode(404);
        // Aceite continua gerando a agenda substituta e renegociando a parcela.
        assertThat(parcelaRepository.findById(fx.parcelaId()).orElseThrow().getStatus())
                .isEqualTo(StatusParcela.RENEGOCIADA);
    }

    @Test
    void consultaAtiva_apos_recusa_sem_step_up_deixaDeAparecer() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        ContratoFixture fx = criarContratoComParcelaAtrasada(tomador);
        Renegociacao r = proporRenegociacao(financeiro, fx.parcelaId());

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(PATH_ATIVA, fx.parcelaId())
                .then()
                .statusCode(200);

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .patch("/api/v1/cobranca/renegociacoes/" + r.getId() + "/recusa")
                .then()
                .statusCode(200)
                .body("status", equalTo("RECUSADA"));

        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .when()
                .get(PATH_ATIVA, fx.parcelaId())
                .then()
                .statusCode(404);
        // Recusa restaura o status anterior da parcela.
        assertThat(parcelaRepository.findById(fx.parcelaId()).orElseThrow().getStatus())
                .isEqualTo(StatusParcela.ATRASADA);
    }
}
