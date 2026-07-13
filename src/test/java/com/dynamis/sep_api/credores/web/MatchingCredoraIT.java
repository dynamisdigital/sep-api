package com.dynamis.sep_api.credores.web;

import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import com.dynamis.sep_api.credores.domain.model.OportunidadeInvestimento;
import com.dynamis.sep_api.credores.domain.vo.StatusMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.infrastructure.persistence.AporteCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.InteresseCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.MatchingCredoraOperacaoRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OportunidadeInvestimentoRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.PerfilCredoraRepository;
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

/**
 * E2E backend do matching assistido credora-operacao (Sprint 30 Task 30.6). Sobe Spring Boot
 * completo + Postgres local ({@code sep_test}) e valida o fluxo {@code GET /sugestoes
 * (refresh-on-read) -> GET /{id} -> POST /decisao}: geracao idempotente sem duplicata, step-up
 * estrito real na decisao, 401/403 pela security chain real, 409 em replay de decisao, auditoria
 * CREDORA_MATCHING_* unica e confirmacao sem criacao de aporte (fluxo Sprint 29 separado).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MatchingCredoraIT {

    private static final String CNPJ_A = "11222333000181";
    private static final String SENHA = "senha-passphrase-segura";
    private static final String PATH_SUGESTOES = "/api/v1/credores/matching/sugestoes";
    private static final String PATH_SUGESTAO = "/api/v1/credores/matching/{sugestaoId}";
    private static final String PATH_DECISAO = "/api/v1/credores/matching/{sugestaoId}/decisao";

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
    MatchingCredoraOperacaoRepository matchingRepository;

    @Autowired
    AporteCredoraRepository aporteRepository;

    @Autowired
    PropostaCreditoRepository propostaRepository;

    @Autowired
    ContratoRepository contratoRepository;

    @Autowired
    AuditLogSegurancaRepository auditLogRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    StepUpTokenService stepUpTokenService;

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
            throw new IllegalStateException("MatchingCredoraIT deve rodar apenas no banco sep_test; URL: " + url);
        }
        matchingRepository.deleteAll();
        aporteRepository.deleteAll();
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

    /** Contrato do tomador (assinado ou nao) + proposta aprovada, para ancorar a operacao. */
    private UUID criarContrato(boolean assinado, UUID[] propostaIdOut) {
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
        propostaIdOut[0] = p.getId();
        Contrato c = Contrato.criar(p.getId(), tomador.getId(), TipoContrato.MUTUO);
        c.adicionarVersao("conteudo do contrato de teste", "a".repeat(64));
        c.marcarAceito();
        c.marcarEmAssinatura();
        if (assinado) {
            c.marcarAssinado();
        }
        return contratoRepository.saveAndFlush(c).getId();
    }

    /**
     * Semeia o par elegivel completo: credora ATIVA/ELEGIVEL + contrato (assinado ou nao) +
     * oportunidade com valor + operacao ASSOCIADA vinculada a oportunidade.
     */
    private UUID seedOperacaoElegivel(UUID credoraId, boolean contratoAssinado) {
        UUID[] propostaIdOut = new UUID[1];
        UUID contratoId = criarContrato(contratoAssinado, propostaIdOut);
        OportunidadeInvestimento oportunidade = oportunidadeRepository.saveAndFlush(OportunidadeInvestimento.criar(
                propostaIdOut[0], contratoId, new BigDecimal("10000.00"), 12, new BigDecimal("1.50")));
        OperacaoFinanciada operacao =
                OperacaoFinanciada.associar(credoraId, contratoId, oportunidade.getId(), "Seed IT matching");
        return operacaoRepository.saveAndFlush(operacao).getId();
    }

    private long auditCount(TipoEventoSeguranca tipo) {
        return auditLogRepository.findAll().stream()
                .filter(a -> a.getTipo() == tipo)
                .count();
    }

    // ============== cenarios ==============

    @Test
    void fluxoCompleto_refreshListaConsultaConfirmaSemCriarAporte() {
        Autenticado credoraUser = criarELogar(Role.CLIENTE, false);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        UUID credoraId = cadastrarCredoraElegivel(credoraUser, CNPJ_A);
        UUID operacaoId = seedOperacaoElegivel(credoraId, true);

        // refresh-on-read gera a sugestao do par elegivel — DTO minimo sem campos internos
        String sugestaoId = RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get(PATH_SUGESTOES)
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].operacaoId", equalTo(operacaoId.toString()))
                .body("[0].empresaCredoraId", equalTo(credoraId.toString()))
                .body("[0].status", equalTo("SUGERIDA"))
                .body("[0].valorElegivel", equalTo(10000.00f))
                .body("[0]", not(hasKey("motivoDecisaoSanitizado")))
                .body("[0]", not(hasKey("decididoPorUsuarioId")))
                .body("[0]", not(hasKey("criteriosSnapshot")))
                .extract()
                .path("[0].id");
        assertThat(auditCount(TipoEventoSeguranca.CREDORA_MATCHING_SUGERIDA)).isEqualTo(1);

        // novo refresh nao duplica (idempotente) — mesma sugestao, mesma auditoria
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get(PATH_SUGESTOES)
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(sugestaoId));
        assertThat(auditCount(TipoEventoSeguranca.CREDORA_MATCHING_SUGERIDA)).isEqualTo(1);

        // consulta individual
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get(PATH_SUGESTAO, sugestaoId)
                .then()
                .statusCode(200)
                .body("id", equalTo(sugestaoId))
                .body("criterios", hasSize(7));

        // decisao CONFIRMAR com step-up estrito real
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", stepUp(financeiro))
                .contentType(ContentType.JSON)
                .body("{\"acao\":\"CONFIRMAR\",\"motivo\":\"aderente a carteira\"}")
                .when()
                .post(PATH_DECISAO, sugestaoId)
                .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMADA"))
                .body("$", not(hasKey("motivoDecisaoSanitizado")));
        assertThat(auditCount(TipoEventoSeguranca.CREDORA_MATCHING_CONFIRMADA)).isEqualTo(1);

        // confirmacao NAO cria aporte nem movimenta escrow (fluxo Sprint 29 separado)
        assertThat(aporteRepository.count()).isZero();

        // estado persistido terminal
        assertThat(matchingRepository
                        .findById(UUID.fromString(sugestaoId))
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StatusMatchingCredoraOperacao.CONFIRMADA);

        // decisao repetida sobre terminal -> 409 e auditoria nao duplica
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .header("X-Step-Up-Token", stepUp(financeiro))
                .contentType(ContentType.JSON)
                .body("{\"acao\":\"REJEITAR\"}")
                .when()
                .post(PATH_DECISAO, sugestaoId)
                .then()
                .statusCode(409);
        assertThat(auditCount(TipoEventoSeguranca.CREDORA_MATCHING_CONFIRMADA)).isEqualTo(1);
        assertThat(auditCount(TipoEventoSeguranca.CREDORA_MATCHING_REJEITADA)).isZero();

        // par confirmado nao volta a ser sugerido em refresh futuro
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get(PATH_SUGESTOES)
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void rejeitarComStepUp_paraDeSugerirOPar() {
        Autenticado credoraUser = criarELogar(Role.CLIENTE, false);
        Autenticado admin = criarELogar(Role.ADMIN, true);
        UUID credoraId = cadastrarCredoraElegivel(credoraUser, CNPJ_A);
        seedOperacaoElegivel(credoraId, true);

        String sugestaoId = RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .get(PATH_SUGESTOES)
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .extract()
                .path("[0].id");

        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .header("X-Step-Up-Token", stepUp(admin))
                .contentType(ContentType.JSON)
                .body("{\"acao\":\"REJEITAR\",\"motivo\":\"fora do apetite\"}")
                .when()
                .post(PATH_DECISAO, sugestaoId)
                .then()
                .statusCode(200)
                .body("status", equalTo("REJEITADA"));
        assertThat(auditCount(TipoEventoSeguranca.CREDORA_MATCHING_REJEITADA)).isEqualTo(1);

        // rejeitado nao e re-sugerido
        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .get(PATH_SUGESTOES)
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
        assertThat(auditCount(TipoEventoSeguranca.CREDORA_MATCHING_SUGERIDA)).isEqualTo(1);
    }

    @Test
    void decisaoSemStepUpEstrito_403() {
        Autenticado credoraUser = criarELogar(Role.CLIENTE, false);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        UUID credoraId = cadastrarCredoraElegivel(credoraUser, CNPJ_A);
        seedOperacaoElegivel(credoraId, true);

        String sugestaoId = RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get(PATH_SUGESTOES)
                .then()
                .statusCode(200)
                .extract()
                .path("[0].id");

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .contentType(ContentType.JSON)
                .body("{\"acao\":\"CONFIRMAR\"}")
                .when()
                .post(PATH_DECISAO, sugestaoId)
                .then()
                .statusCode(403);

        assertThat(matchingRepository
                        .findById(UUID.fromString(sugestaoId))
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(StatusMatchingCredoraOperacao.SUGERIDA);
        assertThat(auditCount(TipoEventoSeguranca.CREDORA_MATCHING_CONFIRMADA)).isZero();
    }

    @Test
    void semAutenticacao_401() {
        RestAssured.given().when().get(PATH_SUGESTOES).then().statusCode(401);
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"acao\":\"CONFIRMAR\"}")
                .when()
                .post(PATH_DECISAO, UUID.randomUUID())
                .then()
                .statusCode(401);
    }

    @Test
    void clienteSemRoleOperacional_403() {
        Autenticado cliente = criarELogar(Role.CLIENTE, false);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get(PATH_SUGESTOES)
                .then()
                .statusCode(403);
    }

    @Test
    void contratoNaoAssinado_naoGeraSugestao() {
        Autenticado credoraUser = criarELogar(Role.CLIENTE, false);
        Autenticado financeiro = criarELogar(Role.FINANCEIRO, true);
        UUID credoraId = cadastrarCredoraElegivel(credoraUser, CNPJ_A);
        seedOperacaoElegivel(credoraId, false);

        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get(PATH_SUGESTOES)
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
        assertThat(auditCount(TipoEventoSeguranca.CREDORA_MATCHING_SUGERIDA)).isZero();
    }
}
