package com.dynamis.sep_api.contratos.web;

import com.dynamis.sep_api.contratos.application.usecase.GerarContratoUseCase;
import com.dynamis.sep_api.contratos.application.usecase.command.GerarContratoCommand;
import com.dynamis.sep_api.contratos.domain.exception.PropostaNaoAprovadaException;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.infrastructure.persistence.AceiteContratoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.DocumentoAssinadoBlobRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.DocumentoAssinadoRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.EnvelopeAssinaturaRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.EventoAssinaturaRepository;
import com.dynamis.sep_api.contratos.infrastructure.persistence.VersaoContratoRepository;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E2E backend do fluxo de formalizacao contratual (Sprint 10 Task 10.8). Sobe Spring Boot completo
 * em RANDOM_PORT + Postgres local (profile {@code test}, banco {@code sep_test}).
 *
 * <p>Cobertura obrigatoria do spec:
 *
 * <ul>
 *   <li>Fluxo feliz: proposta APROVADA -> contrato gerado em AGUARDANDO_ACEITE -> tomador
 *       consulta -> aceita -> ACEITO + audit log;
 *   <li>Tomador alheio tenta aceitar -> 403;
 *   <li>Financeiro cancela em AGUARDANDO_ACEITE -> CANCELADO + audit log;
 *   <li>Financeiro tenta cancelar ACEITO -> 409;
 *   <li>Listar versoes -> array com 1 elemento;
 *   <li>Consulta por proposta como financeiro -> 200;
 *   <li>Cliente alheio nao consulta contrato de outro tomador -> 403;
 *   <li>Cancelamento sem role -> 403;
 *   <li>Cancelamento com payload invalido -> 400.
 * </ul>
 *
 * <p>Step-up: usuarios criados com {@code mfaHabilitado=false} — {@code StepUpEnforcementAspect}
 * bypassa o token (limitacao documentada na Sprint 10 fix code review). Cenarios de step-up real
 * sao cobertos via {@code @WebMvcTest} em {@code ContratoControllerTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ContratoIT {

    @DynamicPropertySource
    static void configurarTest(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
        // Sprint 11: desliga listener de envio automatico para assinatura digital — esses ITs
        // validam apenas o ciclo de aceite/regeneracao/cancelamento (Sprint 10). Fluxo completo
        // com envelope e testado no AssinaturaIT (Task 11.9).
        registry.add("app.assinatura.auto-envio-pos-aceite", () -> "false");
    }

    @LocalServerPort
    int port;

    @Autowired
    ContratoRepository contratoRepository;

    @Autowired
    VersaoContratoRepository versaoContratoRepository;

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
    GerarContratoUseCase gerarContratoUseCase;

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
            throw new IllegalStateException("ContratoIT deve rodar apenas no banco sep_test; URL atual: " + url + ". "
                    + "Crie o banco com: createdb -h localhost -U sep sep_test");
        }
        // Sprint 11: envelope_assinatura.versao_id REFERENCES versao_contrato (ON DELETE RESTRICT)
        // → limpar tabelas de assinatura ANTES de aceite/contrato/versao.
        documentoAssinadoRepository.deleteAll();
        documentoAssinadoBlobRepository.deleteAll();
        eventoAssinaturaRepository.deleteAll();
        envelopeAssinaturaRepository.deleteAll();
        // aceite_contrato.versao_id REFERENCES versao_contrato — limpar aceites primeiro
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

    // ============== Fixtures ==============

    private record Autenticado(UUID id, String email, String token) {}

    private Autenticado criarClienteELogar() {
        return criarELogar(Role.CLIENTE);
    }

    private Autenticado criarFinanceiroELogar() {
        return criarELogar(Role.FINANCEIRO);
    }

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

    /**
     * Atalho: cria proposta via API, aguarda PRE_APROVADA, registra parecer financeiro APROVAR e
     * aguarda contrato gerado pelo listener AFTER_COMMIT. Retorna id da proposta APROVADA.
     */
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

        Autenticado financeiro = criarFinanceiroELogar();
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
        // PropostaAprovadaListener gera contrato AFTER_COMMIT
        pollUntil(() -> contratoRepository.findByPropostaId(propId).isPresent(), "contrato gerado");
        return propId;
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

    // ============== Cenarios ==============

    @Test
    void fluxoFelizPropostaAprovadaDisparaContratoTomadorAceitaEAudit() {
        Autenticado cliente = criarClienteELogar();
        UUID propostaId = criarPropostaAprovadaEAguardarContrato(cliente);

        UUID contratoId =
                contratoRepository.findByPropostaId(propostaId).orElseThrow().getId();

        // Contrato em AGUARDANDO_ACEITE + audit CONTRATO_GERADO
        assertThat(contratoRepository.findByPropostaId(propostaId).orElseThrow().getStatus())
                .isEqualTo(StatusFormalizacao.AGUARDANDO_ACEITE);
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.CONTRATO_GERADO))
                .hasSize(1));

        // Tomador consulta por id e ve status
        String status = RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/contratos/" + contratoId)
                .then()
                .statusCode(200)
                .extract()
                .path("status");
        assertThat(status).isEqualTo("AGUARDANDO_ACEITE");

        // Tomador aceita (mfa=false bypassa step-up)
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .when()
                .patch("/api/v1/contratos/" + contratoId + "/aceite")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("ACEITO"));

        // Estado e audit CONTRATO_ACEITO
        assertThat(contratoRepository.findById(contratoId).orElseThrow().getStatus())
                .isEqualTo(StatusFormalizacao.ACEITO);
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.CONTRATO_ACEITO))
                .hasSize(1));
    }

    @Test
    void tomadorAlheioTentaAceitarRetorna403() {
        Autenticado cliente = criarClienteELogar();
        Autenticado outroCliente = criarClienteELogar();
        UUID propostaId = criarPropostaAprovadaEAguardarContrato(cliente);
        UUID contratoId =
                contratoRepository.findByPropostaId(propostaId).orElseThrow().getId();

        RestAssured.given()
                .header("Authorization", "Bearer " + outroCliente.token())
                .when()
                .patch("/api/v1/contratos/" + contratoId + "/aceite")
                .then()
                .statusCode(403);
    }

    @Test
    void financeiroCancelaAguardandoAceiteEAudit() {
        Autenticado cliente = criarClienteELogar();
        UUID propostaId = criarPropostaAprovadaEAguardarContrato(cliente);
        UUID contratoId =
                contratoRepository.findByPropostaId(propostaId).orElseThrow().getId();

        Autenticado financeiro = criarFinanceiroELogar();
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .contentType(ContentType.JSON)
                .body("{\"justificativa\":\"Cancelado por divergencia operacional antes do aceite.\"}")
                .when()
                .post("/api/v1/contratos/" + contratoId + "/cancelar")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("CANCELADO"));

        assertThat(contratoRepository.findById(contratoId).orElseThrow().getStatus())
                .isEqualTo(StatusFormalizacao.CANCELADO);
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        financeiro.id(), TipoEventoSeguranca.CONTRATO_CANCELADO))
                .hasSize(1));
    }

    @Test
    void financeiroTentaCancelarAceitoRetorna409() {
        Autenticado cliente = criarClienteELogar();
        UUID propostaId = criarPropostaAprovadaEAguardarContrato(cliente);
        UUID contratoId =
                contratoRepository.findByPropostaId(propostaId).orElseThrow().getId();

        // Tomador aceita primeiro
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .patch("/api/v1/contratos/" + contratoId + "/aceite")
                .then()
                .statusCode(200);

        Autenticado financeiro = criarFinanceiroELogar();
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .contentType(ContentType.JSON)
                .body("{\"justificativa\":\"Tentativa indevida de cancelar apos aceite.\"}")
                .when()
                .post("/api/v1/contratos/" + contratoId + "/cancelar")
                .then()
                .statusCode(409);
    }

    @Test
    void listarVersoesIncluiHistorico() {
        Autenticado cliente = criarClienteELogar();
        UUID propostaId = criarPropostaAprovadaEAguardarContrato(cliente);
        UUID contratoId =
                contratoRepository.findByPropostaId(propostaId).orElseThrow().getId();

        Integer size = RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/contratos/" + contratoId + "/versoes")
                .then()
                .statusCode(200)
                .extract()
                .path("size()");
        assertThat(size).isEqualTo(1);
    }

    @Test
    void consultaPorPropostaComoFinanceiroAcessaQualquer() {
        Autenticado cliente = criarClienteELogar();
        UUID propostaId = criarPropostaAprovadaEAguardarContrato(cliente);

        Autenticado financeiro = criarFinanceiroELogar();
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .when()
                .get("/api/v1/contratos/proposta/" + propostaId)
                .then()
                .statusCode(200);
    }

    @Test
    void consultaPorPropostaClienteAlheioRetorna403() {
        Autenticado cliente = criarClienteELogar();
        Autenticado outroCliente = criarClienteELogar();
        UUID propostaId = criarPropostaAprovadaEAguardarContrato(cliente);

        RestAssured.given()
                .header("Authorization", "Bearer " + outroCliente.token())
                .when()
                .get("/api/v1/contratos/proposta/" + propostaId)
                .then()
                .statusCode(403);
    }

    @Test
    void cancelamentoComoClienteRetorna403() {
        Autenticado cliente = criarClienteELogar();
        UUID propostaId = criarPropostaAprovadaEAguardarContrato(cliente);
        UUID contratoId =
                contratoRepository.findByPropostaId(propostaId).orElseThrow().getId();

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body("{\"justificativa\":\"tentativa indevida do tomador\"}")
                .when()
                .post("/api/v1/contratos/" + contratoId + "/cancelar")
                .then()
                .statusCode(403);
    }

    @Test
    void cancelamentoComJustificativaCurtaRetorna400() {
        Autenticado cliente = criarClienteELogar();
        UUID propostaId = criarPropostaAprovadaEAguardarContrato(cliente);
        UUID contratoId =
                contratoRepository.findByPropostaId(propostaId).orElseThrow().getId();

        Autenticado financeiro = criarFinanceiroELogar();
        RestAssured.given()
                .header("Authorization", "Bearer " + financeiro.token())
                .contentType(ContentType.JSON)
                .body("{\"justificativa\":\"curta\"}")
                .when()
                .post("/api/v1/contratos/" + contratoId + "/cancelar")
                .then()
                .statusCode(400);
    }

    @Test
    void regeneracaoEmAguardandoAceiteCriaNovaVersaoEMantemEstado() {
        // Spec line 294: re-geracao em AGUARDANDO_ACEITE deve criar nova versao e manter estado.
        // GerarContratoUseCase nao tem endpoint REST nesta sprint (so disparado pelo listener);
        // chamamos diretamente via comando "manual" para cobrir o cenario E2E.
        Autenticado cliente = criarClienteELogar();
        UUID propostaId = criarPropostaAprovadaEAguardarContrato(cliente);
        UUID contratoId =
                contratoRepository.findByPropostaId(propostaId).orElseThrow().getId();
        assertThat(contratoRepository.findById(contratoId).orElseThrow().getStatus())
                .isEqualTo(StatusFormalizacao.AGUARDANDO_ACEITE);

        gerarContratoUseCase.executar(GerarContratoCommand.manual(propostaId));

        Integer size = RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/contratos/" + contratoId + "/versoes")
                .then()
                .statusCode(200)
                .extract()
                .path("size()");
        assertThat(size).isEqualTo(2);
        assertThat(contratoRepository.findById(contratoId).orElseThrow().getStatus())
                .isEqualTo(StatusFormalizacao.AGUARDANDO_ACEITE);
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.CONTRATO_NOVA_VERSAO))
                .hasSize(1));
    }

    @Test
    void geracaoComPropostaNaoAprovadaLanca422() {
        // Spec line 293: geracao com proposta nao APROVADA deve falhar.
        // GerarContratoUseCase nao tem endpoint REST; chamamos direto e validamos a excecao.
        Autenticado cliente = criarClienteELogar();
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
        // Aguardamos PRE_APROVADA (motor AFTER_COMMIT) — ainda nao APROVADA.
        pollUntil(
                () -> propostaRepository.findById(propId).orElseThrow().getStatus() == StatusProposta.PRE_APROVADA,
                "PRE_APROVADA");

        assertThatThrownBy(() -> gerarContratoUseCase.executar(GerarContratoCommand.manual(propId)))
                .isInstanceOf(PropostaNaoAprovadaException.class);
        assertThat(contratoRepository.findByPropostaId(propId)).isEmpty();
    }
}
