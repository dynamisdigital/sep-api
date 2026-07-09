package com.dynamis.sep_api.credores.web;

import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credores.application.dto.ReconciliarAporteCredoraCommand;
import com.dynamis.sep_api.credores.application.usecase.ReconciliarAporteCredoraUseCase;
import com.dynamis.sep_api.credores.domain.model.AporteCredora;
import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import com.dynamis.sep_api.credores.domain.vo.StatusAporteCredora;
import com.dynamis.sep_api.credores.infrastructure.persistence.AporteCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.InteresseCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OportunidadeInvestimentoRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.PerfilCredoraRepository;
import com.dynamis.sep_api.escrow.domain.vo.StatusMovimentacao;
import com.dynamis.sep_api.escrow.infrastructure.persistence.ContaEscrowRepository;
import com.dynamis.sep_api.escrow.infrastructure.persistence.MovimentacaoEscrowRepository;
import com.dynamis.sep_api.escrow.infrastructure.persistence.WalletRepository;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
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
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

/**
 * E2E backend do aporte assistido da credora (Sprint 29 Task 29.7). Sobe Spring Boot completo +
 * Postgres local ({@code sep_test}) e valida o fluxo {@code POST -> escrow fake -> reconciliacao ->
 * GET}: step-up estrito real, idempotencia por Idempotency-Key, elegibilidade (contrato ASSINADO),
 * leitura owner-scoped, credito de wallet somente na liquidacao e auditoria unica. A reconciliacao
 * e acionada por chamada direta ao use case — e assim que o "callback do provider fake" funciona
 * nesta fase (sem webhook; Fase 5 pluga o provider real).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AporteCredoraIT {

    private static final String CNPJ_A = "11222333000181";
    private static final String CNPJ_B = "11444777000161";
    private static final String SENHA = "senha-passphrase-segura";
    private static final String PATH = "/api/v1/credores/operacoes/{operacaoId}/aportes";

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

    @Autowired
    SolicitacaoOnboardingRepository solicitacaoRepository;

    @Autowired
    KybEmpresaRepository kybRepository;

    @Autowired
    EmpresaCredoraRepository empresaRepository;

    @Autowired
    PerfilCredoraRepository perfilRepository;

    @Autowired
    OportunidadeInvestimentoRepository oportunidadeRepository;

    @Autowired
    InteresseCredoraRepository interesseRepository;

    @Autowired
    OperacaoFinanciadaRepository operacaoRepository;

    @Autowired
    AporteCredoraRepository aporteRepository;

    @Autowired
    PropostaCreditoRepository propostaRepository;

    @Autowired
    ContratoRepository contratoRepository;

    @Autowired
    MovimentacaoEscrowRepository movimentacaoRepository;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    ContaEscrowRepository contaEscrowRepository;

    @Autowired
    AuditLogSegurancaRepository auditLogRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    StepUpTokenService stepUpTokenService;

    @Autowired
    ReconciliarAporteCredoraUseCase reconciliarUseCase;

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
            throw new IllegalStateException("AporteCredoraIT deve rodar apenas no banco sep_test; URL: " + url);
        }
        aporteRepository.deleteAll();
        movimentacaoRepository.deleteAll();
        walletRepository.deleteAll();
        contaEscrowRepository.deleteAll();
        operacaoRepository.deleteAll();
        interesseRepository.deleteAll();
        perfilRepository.deleteAll();
        empresaRepository.deleteAll();
        oportunidadeRepository.deleteAll();
        contratoRepository.deleteAll();
        propostaRepository.deleteAll();
        kybRepository.deleteAll();
        solicitacaoRepository.deleteAll();
        auditLogRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // ============== fixtures ==============

    private record Autenticado(UUID id, String token) {}

    private record ContratoTomador(UUID contratoId, UUID propostaId, UUID tomadorId) {}

    private static final String[] CPFS = {"52998224725", "11144477735", "87748248800", "39053344705"};
    private final AtomicInteger cpfCursor = new AtomicInteger();

    private Autenticado criarELogar(Role role, boolean mfa) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = role.name().toLowerCase() + "-" + suffix + "@sep.test";
        Usuario u;
        if (role == Role.CLIENTE) {
            String id = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body("{\"username\":\"" + email + "\",\"password\":\"" + SENHA + "\"}")
                    .when()
                    .post("/api/v1/usuarios")
                    .then()
                    .statusCode(201)
                    .extract()
                    .path("id");
            u = usuarioRepository.findById(UUID.fromString(id)).orElseThrow();
            if (mfa) {
                u.marcarMfaHabilitado();
                usuarioRepository.saveAndFlush(u);
            }
        } else {
            u = Usuario.criar(email, passwordEncoder.encode(SENHA), role);
            if (mfa) {
                u.marcarMfaHabilitado();
            }
            u = usuarioRepository.saveAndFlush(u);
        }
        String token = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + SENHA + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        return new Autenticado(u.getId(), token);
    }

    private String stepUp(Autenticado autenticado) {
        return stepUpTokenService.emitir(autenticado.id()).token();
    }

    private UUID cadastrarCredoraElegivel(Autenticado cliente, String cnpj) {
        UUID onbId = criarOnboardingEmpresa(cliente.id(), cnpj);
        return UUID.fromString(RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body("{\"onboardingId\":\"" + onbId + "\",\"tipoCredora\":\"EMPRESA\",\"capacidadeAporte\":100000.00}")
                .when()
                .post("/api/v1/credores")
                .then()
                .statusCode(201)
                .extract()
                .path("id"));
    }

    private UUID criarOnboardingEmpresa(UUID usuarioId, String cnpj) {
        SolicitacaoOnboarding s = SolicitacaoOnboarding.criarEmpresa(usuarioId, cnpj, "Credora Teste LTDA");
        s.registrarDocumentoEnviado();
        s.marcarEmVerificacao("fake-" + UUID.randomUUID());
        s.finalizar(StatusOnboarding.APROVADO);
        s.marcarAprovadoFinal();
        UUID id = solicitacaoRepository.saveAndFlush(s).getId();
        kybRepository.saveAndFlush(KybEmpresa.criar(
                id, new Cnpj(cnpj), "Credora Teste LTDA", null, TipoSocietario.LTDA, PorteEmpresa.EPP));
        return id;
    }

    private ContratoTomador criarContrato(boolean assinado) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String cpf = CPFS[cpfCursor.getAndIncrement() % CPFS.length];
        Usuario tomador = usuarioRepository.saveAndFlush(
                Usuario.criar("tomador-" + suffix + "@sep.test", passwordEncoder.encode(SENHA), Role.CLIENTE));
        SolicitacaoOnboarding onb = SolicitacaoOnboarding.criarPessoa(
                tomador.getId(), new Cpf(cpf), "Tomador Teste", LocalDate.of(1990, 1, 1));
        onb.registrarDocumentoEnviado();
        onb.marcarEmVerificacao("fake-" + UUID.randomUUID());
        onb.finalizar(StatusOnboarding.APROVADO);
        onb.marcarAprovadoFinal();
        UUID onbId = solicitacaoRepository.saveAndFlush(onb).getId();

        PropostaCredito p = PropostaCredito.criar(
                tomador.getId(), onbId, TipoOperacao.OUTROS, new Money(new BigDecimal("10000.00"), "BRL"), 12);
        p.registrarDecisaoManual(DecisaoParecer.APROVAR);
        p = propostaRepository.saveAndFlush(p);
        Contrato c = Contrato.criar(p.getId(), tomador.getId(), TipoContrato.MUTUO);
        c.adicionarVersao("conteudo do contrato de teste", "a".repeat(64));
        c.marcarAceito();
        c.marcarEmAssinatura();
        if (assinado) {
            c.marcarAssinado();
        }
        UUID contratoId = contratoRepository.saveAndFlush(c).getId();
        return new ContratoTomador(contratoId, p.getId(), tomador.getId());
    }

    private String associar(Autenticado admin, UUID credoraId, UUID contratoId) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .header("X-Step-Up-Token", stepUp(admin))
                .contentType(ContentType.JSON)
                .body("{\"empresaCredoraId\":\"" + credoraId + "\",\"contratoId\":\"" + contratoId
                        + "\",\"justificativa\":\"Associacao operacional assistida do teste\"}")
                .when()
                .post("/api/v1/credores/carteira/operacoes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    /** Operacao com contrato NAO assinado, semeada direto (a associacao assistida exige ASSINADO). */
    private UUID seedOperacaoComContratoNaoAssinado(UUID credoraId) {
        ContratoTomador ct = criarContrato(false);
        OperacaoFinanciada operacao =
                OperacaoFinanciada.associar(credoraId, ct.contratoId(), null, "Seed IT contrato nao assinado");
        return operacaoRepository.saveAndFlush(operacao).getId();
    }

    private long auditCount(TipoEventoSeguranca tipo) {
        return auditLogRepository.findAll().stream()
                .filter(a -> a.getTipo() == tipo)
                .count();
    }

    // ============== cenarios ==============

    @Test
    void fluxoCompleto_postReplayReconciliaLiquidaEListaOwnerScoped() {
        Autenticado admin = criarELogar(Role.ADMIN, true);
        Autenticado credora = criarELogar(Role.CLIENTE, false);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        UUID credoraId = cadastrarCredoraElegivel(credora, CNPJ_A);
        ContratoTomador ct = criarContrato(true);
        String operacaoId = associar(admin, credoraId, ct.contratoId());

        // POST cria aporte em operacao elegivel — 201 EM_PROCESSAMENTO, DTO minimo
        String aporteId = RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", stepUp(financeiro))
                .header("Idempotency-Key", "idem-aporte-1")
                .contentType(ContentType.JSON)
                .body("{\"valor\":2500.00}")
                .when()
                .post(PATH, operacaoId)
                .then()
                .statusCode(201)
                .body("operacaoId", equalTo(operacaoId))
                .body("status", equalTo("EM_PROCESSAMENTO"))
                .body("valor", equalTo(2500.00f))
                .body("$", not(hasKey("idempotencyKey")))
                .body("$", not(hasKey("referenciaEscrow")))
                .body("$", not(hasKey("motivoFalhaSanitizado")))
                .extract()
                .path("id");

        // replay com mesma Idempotency-Key retorna o mesmo aporte — 200
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", stepUp(financeiro))
                .header("Idempotency-Key", "idem-aporte-1")
                .contentType(ContentType.JSON)
                .body("{\"valor\":2500.00}")
                .when()
                .post(PATH, operacaoId)
                .then()
                .statusCode(200)
                .body("id", equalTo(aporteId));
        assertThat(auditCount(TipoEventoSeguranca.CREDORA_APORTE_REGISTRADO)).isEqualTo(1);

        // reconciliacao fake liquida (chamada direta ao use case — callback do provider fake)
        AporteCredora aporte = aporteRepository
                .findByOperacaoIdAndIdempotencyKey(UUID.fromString(operacaoId), "idem-aporte-1")
                .orElseThrow();
        reconciliarUseCase.executar(
                new ReconciliarAporteCredoraCommand(aporte.getReferenciaEscrow(), StatusAporteCredora.LIQUIDADO, null));

        // escrow: movimentacao Aporte liquidada e wallet creditada somente agora
        assertThat(movimentacaoRepository
                        .findByIdempotencyKey("aporte:" + aporteId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StatusMovimentacao.LIQUIDADA);
        assertThat(walletRepository
                        .findByPropostaId(ct.propostaId())
                        .orElseThrow()
                        .getSaldo())
                .isEqualByComparingTo("2500.00");
        assertThat(auditCount(TipoEventoSeguranca.CREDORA_APORTE_LIQUIDADO)).isEqualTo(1);

        // GET owner-scoped: credora dona ve o aporte liquidado, DTO minimo
        RestAssured.given()
                .header("Authorization", "Bearer " + credora.token())
                .when()
                .get(PATH, operacaoId)
                .then()
                .statusCode(200)
                .body("[0].id", equalTo(aporteId))
                .body("[0].status", equalTo("LIQUIDADO"))
                .body("[0]", not(hasKey("referenciaEscrow")))
                .body("[0]", not(hasKey("idempotencyKey")));

        // GET visao operacional (financeiro) tambem enxerga
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get(PATH, operacaoId)
                .then()
                .statusCode(200)
                .body("[0].id", equalTo(aporteId));
    }

    @Test
    void reconciliacaoFalha_marcaFalhouSemCreditarWallet() {
        Autenticado admin = criarELogar(Role.ADMIN, true);
        Autenticado credora = criarELogar(Role.CLIENTE, false);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        UUID credoraId = cadastrarCredoraElegivel(credora, CNPJ_A);
        ContratoTomador ct = criarContrato(true);
        String operacaoId = associar(admin, credoraId, ct.contratoId());

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", stepUp(financeiro))
                .header("Idempotency-Key", "idem-aporte-falha")
                .contentType(ContentType.JSON)
                .body("{\"valor\":900.00}")
                .when()
                .post(PATH, operacaoId)
                .then()
                .statusCode(201);

        AporteCredora aporte = aporteRepository
                .findByOperacaoIdAndIdempotencyKey(UUID.fromString(operacaoId), "idem-aporte-falha")
                .orElseThrow();
        reconciliarUseCase.executar(new ReconciliarAporteCredoraCommand(
                aporte.getReferenciaEscrow(), StatusAporteCredora.FALHOU, "Aporte recusado na liquidacao"));

        assertThat(walletRepository
                        .findByPropostaId(ct.propostaId())
                        .orElseThrow()
                        .getSaldo())
                .isEqualByComparingTo("0.00");
        assertThat(auditCount(TipoEventoSeguranca.CREDORA_APORTE_FALHOU)).isEqualTo(1);

        RestAssured.given()
                .header("Authorization", "Bearer " + credora.token())
                .when()
                .get(PATH, operacaoId)
                .then()
                .statusCode(200)
                .body("[0].status", equalTo("FALHOU"))
                .body("[0]", not(hasKey("motivoFalhaSanitizado")));
    }

    @Test
    void contratoNaoAssinado_409() {
        Autenticado credoraUser = criarELogar(Role.CLIENTE, false);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        UUID credoraId = cadastrarCredoraElegivel(credoraUser, CNPJ_A);
        UUID operacaoId = seedOperacaoComContratoNaoAssinado(credoraId);

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", stepUp(financeiro))
                .header("Idempotency-Key", "idem-409")
                .contentType(ContentType.JSON)
                .body("{\"valor\":100.00}")
                .when()
                .post(PATH, operacaoId)
                .then()
                .statusCode(409);
        assertThat(aporteRepository.count()).isZero();
    }

    @Test
    void financeiroSemStepUpEstrito_403() {
        Autenticado admin = criarELogar(Role.ADMIN, true);
        Autenticado credoraUser = criarELogar(Role.CLIENTE, false);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        UUID credoraId = cadastrarCredoraElegivel(credoraUser, CNPJ_A);
        ContratoTomador ct = criarContrato(true);
        String operacaoId = associar(admin, credoraId, ct.contratoId());

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("Idempotency-Key", "idem-403")
                .contentType(ContentType.JSON)
                .body("{\"valor\":100.00}")
                .when()
                .post(PATH, operacaoId)
                .then()
                .statusCode(403);
        assertThat(aporteRepository.count()).isZero();
    }

    @Test
    void credoraAlheia_404NeutroNoGet() {
        Autenticado admin = criarELogar(Role.ADMIN, true);
        Autenticado dona = criarELogar(Role.CLIENTE, false);
        Autenticado outra = criarELogar(Role.CLIENTE, false);
        UUID credoraDona = cadastrarCredoraElegivel(dona, CNPJ_A);
        cadastrarCredoraElegivel(outra, CNPJ_B);
        ContratoTomador ct = criarContrato(true);
        String operacaoId = associar(admin, credoraDona, ct.contratoId());

        RestAssured.given()
                .header("Authorization", "Bearer " + outra.token())
                .when()
                .get(PATH, operacaoId)
                .then()
                .statusCode(404)
                .body("message", not(equalTo(operacaoId)));
    }
}
