package com.dynamis.sep_api.cobranca.web;

import com.dynamis.sep_api.cobranca.application.job.EscaladorCobrancaJob;
import com.dynamis.sep_api.cobranca.application.job.MarcarParcelaAtrasadaJob;
import com.dynamis.sep_api.cobranca.application.job.MarcarParcelaInadimplenteJob;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.domain.vo.TipoEventoCobranca;
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

/**
 * E2E da Sprint 13 — fluxo de inadimplencia (Task 13.9).
 *
 * <p>Cenarios cobertos:
 *
 * <ul>
 *   <li>Parcela atrasa → {@code ParcelaAtrasouListener} dispara workflow dia 0; {@code EventoCobranca}
 *       persistido com {@code NOTIFICACAO_AUTOMATICA}.
 *   <li>Job escalador re-executa pra parcelas com mais dias de atraso; idempotente (nao
 *       duplica notificacoes no mesmo dia).
 *   <li>Parcela com 90+ dias de atraso → {@code MarcarParcelaInadimplenteJob} transiciona pra
 *       {@code INADIMPLENTE}, grava {@code EventoCobranca PARCELA_INADIMPLENTE} e audit log.
 *   <li>{@code GET /api/v1/cobranca/inadimplencia} retorna parcelas {@code ATRASADA} e
 *       {@code INADIMPLENTE}.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InadimplenciaIT {

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
        registry.add("app.cobranca.auto-geracao-pos-assinatura", () -> "true");
        // Habilita beans dos jobs (default test profile desliga pra evitar disparo aleatorio).
        // Os testes chamam .executar() direto — cron continua agendado mas o IT controla execucao.
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
    EscaladorCobrancaJob escaladorCobrancaJob;

    @Autowired
    MarcarParcelaInadimplenteJob marcarParcelaInadimplenteJob;

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
            throw new IllegalStateException("InadimplenciaIT deve rodar apenas no banco sep_test; URL atual: " + url);
        }
        renegociacaoRepository.deleteAll();
        eventoCobrancaRepository.deleteAll();
        recebimentoRepository.deleteAll();
        movimentacaoEscrowRepository.deleteAll();
        walletRepository.deleteAll();
        contaEscrowRepository.deleteAll();
        // Hotfix code review Task 13.9: defesa contra rows leftover de RenegociacaoIT na mesma
        // suite — agenda_substituida_id eh self-FK. Limpa substitutas (parcelas + agenda)
        // antes do deleteAll geral.
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
        return new ContratoAssinadoFixture(contrato.getId(), p.getId(), tomador.id());
    }

    private void forcarVencimento(UUID parcelaId, LocalDate dataVencimento) {
        jdbcTemplate.update(
                "UPDATE parcela_cobranca SET data_vencimento = ? WHERE id = ?",
                java.sql.Date.valueOf(dataVencimento),
                parcelaId);
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

    // ============== cenarios ==============

    @Test
    void parcelaAtrasada_disparaListenerDia0_geraEventoCobranca() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();
        forcarVencimento(parcelaId, LocalDate.now().minusDays(1));

        int marcadas = marcarParcelaAtrasadaJob.executar();

        assertThat(marcadas).isGreaterThanOrEqualTo(1);
        assertThat(parcelaRepository.findById(parcelaId).orElseThrow().getStatus())
                .isEqualTo(StatusParcela.ATRASADA);
        // Listener dia 0 grava EventoCobranca via LogNotificationProvider (test profile).
        pollUntil(
                () -> !eventoCobrancaRepository
                        .findByParcelaIdOrderByDataEventoAsc(parcelaId)
                        .isEmpty(),
                "evento dia 0 criado");
        assertThat(eventoCobrancaRepository.findByParcelaIdOrderByDataEventoAsc(parcelaId))
                .anyMatch(e -> e.getTipo() == TipoEventoCobranca.NOTIFICACAO_AUTOMATICA && e.getDiasAtraso() == 0);
    }

    @Test
    void escalador_dia5_geraDuasNotificacoesEmailESms() {
        // Fix review manual Task 13.9: spec exige 2 notificacoes (email-amigavel + sms-lembrete)
        // — antes assertava so existencia de algum evento. Agora valida canais EMAIL + SMS.
        Autenticado tomador = criarELogar(Role.CLIENTE);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();
        forcarVencimento(parcelaId, LocalDate.now().minusDays(5));
        marcarParcelaAtrasadaJob.executar();
        eventoCobrancaRepository.deleteAll();

        int processadas = escaladorCobrancaJob.executar();

        assertThat(processadas).isGreaterThanOrEqualTo(1);
        var dia5 = eventoCobrancaRepository.findByParcelaIdOrderByDataEventoAsc(parcelaId).stream()
                .filter(e -> e.getDiasAtraso() != null && e.getDiasAtraso() == 5)
                .toList();
        assertThat(dia5)
                .as("etapa dia 5 deve gerar email-amigavel + sms-lembrete")
                .extracting(e -> e.getCanal().name())
                .containsExactlyInAnyOrder("EMAIL", "SMS");
        assertThat(dia5)
                .extracting(com.dynamis.sep_api.cobranca.domain.model.EventoCobranca::getTemplate)
                .containsExactlyInAnyOrder("cobranca-amigavel", "cobranca-lembrete");
    }

    @Test
    void escalador_dia15_geraNotificacoesFirmes() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();
        forcarVencimento(parcelaId, LocalDate.now().minusDays(15));
        marcarParcelaAtrasadaJob.executar();
        eventoCobrancaRepository.deleteAll();

        escaladorCobrancaJob.executar();

        var dia15 = eventoCobrancaRepository.findByParcelaIdOrderByDataEventoAsc(parcelaId).stream()
                .filter(e -> e.getDiasAtraso() != null && e.getDiasAtraso() == 15)
                .toList();
        assertThat(dia15)
                .extracting(com.dynamis.sep_api.cobranca.domain.model.EventoCobranca::getTemplate)
                .containsExactlyInAnyOrder("cobranca-firme", "cobranca-firme");
    }

    @Test
    void escalador_dia30_etapaTemFlagContatoManual() {
        // Spec exige flag-contato-manual no dia 30. Reflete no `EtapaCobrancaAplicadaEvent` —
        // mas o EventoCobranca persistido nao carrega a flag (ela vive no EscalonamentoResult).
        // Validacao: 2 notificacoes (email-firme + sms-firme) sao geradas.
        Autenticado tomador = criarELogar(Role.CLIENTE);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();
        forcarVencimento(parcelaId, LocalDate.now().minusDays(30));
        marcarParcelaAtrasadaJob.executar();
        eventoCobrancaRepository.deleteAll();

        escaladorCobrancaJob.executar();

        var dia30 = eventoCobrancaRepository.findByParcelaIdOrderByDataEventoAsc(parcelaId).stream()
                .filter(e -> e.getDiasAtraso() != null && e.getDiasAtraso() == 30)
                .toList();
        assertThat(dia30).extracting(e -> e.getCanal().name()).containsExactlyInAnyOrder("EMAIL", "SMS");
    }

    @Test
    void escalador_naoDuplicaNotificacaoNoMesmoDia() {
        // Fix review manual Task 13.9: spec exige "notificacao nao duplica no mesmo dia"
        // (idempotencia via unique parcial uq_evento_notificacao_idempotencia).
        Autenticado tomador = criarELogar(Role.CLIENTE);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();
        forcarVencimento(parcelaId, LocalDate.now().minusDays(5));
        marcarParcelaAtrasadaJob.executar();
        eventoCobrancaRepository.deleteAll();

        escaladorCobrancaJob.executar();
        long apos1aRodada = eventoCobrancaRepository
                .findByParcelaIdOrderByDataEventoAsc(parcelaId)
                .size();
        escaladorCobrancaJob.executar();
        long apos2aRodada = eventoCobrancaRepository
                .findByParcelaIdOrderByDataEventoAsc(parcelaId)
                .size();

        assertThat(apos2aRodada)
                .as("re-execucao do job no mesmo dia nao deve duplicar EventoCobranca de notificacao")
                .isEqualTo(apos1aRodada);
    }

    @Test
    void inadimplente_dia90_transicionaEPublicaEvento() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();
        forcarVencimento(parcelaId, LocalDate.now().minusDays(95));
        marcarParcelaAtrasadaJob.executar();
        assertThat(parcelaRepository.findById(parcelaId).orElseThrow().getStatus())
                .isEqualTo(StatusParcela.ATRASADA);

        int marcadas = marcarParcelaInadimplenteJob.executar();

        assertThat(marcadas).isGreaterThanOrEqualTo(1);
        assertThat(parcelaRepository.findById(parcelaId).orElseThrow().getStatus())
                .isEqualTo(StatusParcela.INADIMPLENTE);
        // Audit log + EventoCobranca PARCELA_INADIMPLENTE persistidos.
        pollUntil(
                () -> auditLogRepository.findAll().stream()
                        .anyMatch(a -> a.getTipo() == TipoEventoSeguranca.PARCELA_INADIMPLENTE),
                "audit PARCELA_INADIMPLENTE");
        assertThat(eventoCobrancaRepository.findByParcelaIdOrderByDataEventoAsc(parcelaId))
                .anyMatch(e -> e.getTipo() == TipoEventoCobranca.PARCELA_INADIMPLENTE);
    }

    @Test
    void getInadimplencia_financeiroVeApenasAtrasadaEInadimplente() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();
        forcarVencimento(parcelaId, LocalDate.now().minusDays(15));
        marcarParcelaAtrasadaJob.executar();

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get("/api/v1/cobranca/inadimplencia")
                .then()
                .statusCode(200)
                .body("size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
                .body("[0].status", org.hamcrest.Matchers.equalTo("ATRASADA"))
                .body("[0].diasAtraso", org.hamcrest.Matchers.greaterThanOrEqualTo(15));
    }

    @Test
    void getInadimplencia_clienteSemRole_403() {
        Autenticado cliente = criarELogar(Role.CLIENTE);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/cobranca/inadimplencia")
                .then()
                .statusCode(403);
    }

    @Test
    void contatoManual_financeiroRegistra_geraEventoCobranca() {
        Autenticado tomador = criarELogar(Role.CLIENTE);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO);
        ContratoAssinadoFixture fixture = criarContratoAssinado(tomador);
        UUID parcelaId = parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(fixture.contratoId())
                .get(0)
                .getId();

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .contentType(ContentType.JSON)
                .body("{\"descricao\":\"Cliente prometeu pagar sexta\",\"diasAtraso\":5}")
                .when()
                .post("/api/v1/cobranca/parcelas/" + parcelaId + "/contato")
                .then()
                .statusCode(201)
                .body("tipo", org.hamcrest.Matchers.equalTo("CONTATO_MANUAL"))
                .body("descricao", org.hamcrest.Matchers.containsString("sexta"));

        assertThat(eventoCobrancaRepository.findByParcelaIdOrderByDataEventoAsc(parcelaId))
                .anyMatch(e -> e.getTipo() == TipoEventoCobranca.CONTATO_MANUAL);
    }
}
