package com.dynamis.sep_api.credores.web;

import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.InteresseCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OportunidadeInvestimentoRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.PerfilCredoraRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E backend de oportunidades e carteira da credora (Sprint 17 Task 17.6). Cobre o fluxo
 * oportunidade -> interesse -> carteira, elegibilidade, ownership e auditoria.
 *
 * <p>Onboarding PJ aprovado, credora e proposta APROVADA sao montados via repositorio (atalho).
 * Admin opera com MFA desabilitado (profile test bypassa step-up, como no CreditoIT).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CarteiraCredoraIT {

    private static final String CNPJ_VALIDO = "11222333000181";

    // Corpo do 404 compartilhado por "sem credora" e "sem interesse" (404 neutro anti-enumeracao).
    private static final String INTERESSE_404_MSG =
            "Nenhum interesse ativo encontrado para esta credora na oportunidade";

    @DynamicPropertySource
    static void configurarTest(DynamicPropertyRegistry registry) {
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
            throw new IllegalStateException("CarteiraCredoraIT deve rodar apenas no banco sep_test; URL atual: " + url);
        }
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

    // ============== Fixtures ==============

    private record Autenticado(UUID id, String token) {}

    private Autenticado criarClienteELogar() {
        return criarELogar(Role.CLIENTE);
    }

    private Autenticado criarAdminELogar() {
        return criarELogar(Role.ADMIN);
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
            u = usuarioRepository.saveAndFlush(Usuario.criar(email, passwordEncoder.encode(senha), role));
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
        return new Autenticado(u.getId(), token);
    }

    /** Cria credora ATIVA + ELEGIVEL via fluxo de cadastro (onboarding PJ APROVADO_FINAL). */
    private UUID cadastrarCredoraElegivel(Autenticado cliente, String cnpj) {
        UUID onbId = criarOnboardingEmpresa(cliente.id(), cnpj, StatusOnboarding.APROVADO_FINAL);
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

    private UUID criarOnboardingEmpresa(UUID usuarioId, String cnpj, StatusOnboarding statusFinal) {
        SolicitacaoOnboarding s = SolicitacaoOnboarding.criarEmpresa(usuarioId, cnpj, "Credora Teste LTDA");
        s.registrarDocumentoEnviado();
        s.marcarEmVerificacao("fake-" + UUID.randomUUID());
        if (statusFinal == StatusOnboarding.APROVADO_FINAL) {
            s.finalizar(StatusOnboarding.APROVADO);
            s.marcarAprovadoFinal();
        } else {
            s.finalizar(statusFinal);
        }
        UUID id = solicitacaoRepository.saveAndFlush(s).getId();
        kybRepository.saveAndFlush(KybEmpresa.criar(
                id, new Cnpj(cnpj), "Credora Teste LTDA", null, TipoSocietario.LTDA, PorteEmpresa.EPP));
        return id;
    }

    /** Cria tomador + onboarding PF + proposta APROVADA + contrato; retorna o contratoId. */
    private UUID criarPropostaAprovadaComContrato() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Usuario tomador = usuarioRepository.saveAndFlush(Usuario.criar(
                "tomador-" + suffix + "@sep.test", passwordEncoder.encode("senha-passphrase-segura"), Role.CLIENTE));
        SolicitacaoOnboarding onb = SolicitacaoOnboarding.criarPessoa(
                tomador.getId(), new Cpf("52998224725"), "Tomador Teste", LocalDate.of(1990, 1, 1));
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
        c.marcarAssinado();
        return contratoRepository.saveAndFlush(c).getId();
    }

    private void sincronizar(Autenticado admin) {
        RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .when()
                .post("/api/v1/credores/oportunidades/sync")
                .then()
                .statusCode(200);
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

    private String idPrimeiraOportunidade(Autenticado cliente) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/credores/oportunidades")
                .then()
                .statusCode(200)
                .extract()
                .path("[0].id");
    }

    // ============== Fluxo feliz oportunidade -> interesse -> carteira ==============

    @Test
    void fluxoOportunidadeInteresseCarteiraComAuditoria() {
        Autenticado admin = criarAdminELogar();
        Autenticado cliente = criarClienteELogar();
        UUID credoraId = cadastrarCredoraElegivel(cliente, CNPJ_VALIDO);
        criarPropostaAprovadaComContrato();

        sincronizar(admin);

        String oportunidadeId = idPrimeiraOportunidade(cliente);
        assertThat(oportunidadeId).isNotNull();

        // registrar interesse
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses")
                .then()
                .statusCode(201)
                .body("status", org.hamcrest.Matchers.equalTo("ATIVO"));

        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.CREDORA_INTERESSE_REGISTRADO))
                .hasSize(1));

        // interesse duplicado -> 409
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses")
                .then()
                .statusCode(409);

        // cancelar interesse -> 204 + audit
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .delete("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses/me")
                .then()
                .statusCode(204);
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.CREDORA_INTERESSE_CANCELADO))
                .hasSize(1));

        // admin associa operacao a carteira -> 201 + audit
        String operacaoId = RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .contentType(ContentType.JSON)
                .body("{\"empresaCredoraId\":\"" + credoraId + "\",\"oportunidadeId\":\"" + oportunidadeId
                        + "\",\"justificativa\":\"Associacao operacional assistida do teste\"}")
                .when()
                .post("/api/v1/credores/carteira/operacoes")
                .then()
                .statusCode(201)
                .body("status", org.hamcrest.Matchers.equalTo("ASSOCIADA"))
                .extract()
                .path("id");
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        admin.id(), TipoEventoSeguranca.CREDORA_OPERACAO_ASSOCIADA))
                .hasSize(1));

        // carteira da credora lista a operacao
        Integer total = RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/credores/carteira")
                .then()
                .statusCode(200)
                .extract()
                .path("size()");
        assertThat(total).isEqualTo(1);
        assertThat(operacaoId).isNotNull();
    }

    // ============== Negativos ==============

    @Test
    void credoraInelegivelNaoRegistraInteresse() {
        Autenticado admin = criarAdminELogar();
        Autenticado cliente = criarClienteELogar();
        // credora INELEGIVEL: onboarding REPROVADO
        UUID onbId = criarOnboardingEmpresa(cliente.id(), CNPJ_VALIDO, StatusOnboarding.REPROVADO);
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .contentType(ContentType.JSON)
                .body("{\"onboardingId\":\"" + onbId + "\",\"tipoCredora\":\"EMPRESA\",\"capacidadeAporte\":1000.00}")
                .when()
                .post("/api/v1/credores")
                .then()
                .statusCode(201);

        criarPropostaAprovadaComContrato();
        sincronizar(admin);
        String oportunidadeId = idPrimeiraOportunidade(cliente);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses")
                .then()
                .statusCode(422);
    }

    @Test
    void sincronizarComoNaoAdminRetorna403() {
        Autenticado cliente = criarClienteELogar();
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/credores/oportunidades/sync")
                .then()
                .statusCode(403);
    }

    @Test
    void usuarioSemCredoraNaoListaOportunidades() {
        Autenticado cliente = criarClienteELogar();
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/credores/oportunidades")
                .then()
                .statusCode(404);
    }

    @Test
    void operacaoDeOutraCredoraNaoVisivel() {
        Autenticado admin = criarAdminELogar();
        Autenticado dono = criarClienteELogar();
        Autenticado outro = criarClienteELogar();
        UUID credoraDono = cadastrarCredoraElegivel(dono, CNPJ_VALIDO);
        cadastrarCredoraElegivel(outro, "11444777000161");
        UUID contratoId = criarPropostaAprovadaComContrato();

        String operacaoId = RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
                .contentType(ContentType.JSON)
                .body("{\"empresaCredoraId\":\"" + credoraDono + "\",\"contratoId\":\"" + contratoId
                        + "\",\"justificativa\":\"Associacao do dono\"}")
                .when()
                .post("/api/v1/credores/carteira/operacoes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // outro usuario nao acessa a operacao do dono
        RestAssured.given()
                .header("Authorization", "Bearer " + outro.token())
                .when()
                .get("/api/v1/credores/carteira/" + operacaoId)
                .then()
                .statusCode(404);
    }

    // ============== Leitura do interesse ativo (Sprint 25 - Gate I1) ==============

    @Test
    void interesseAtivoRetorna200ComStatusAtivoESomenteQuatroCampos() {
        Autenticado admin = criarAdminELogar();
        Autenticado cliente = criarClienteELogar();
        cadastrarCredoraElegivel(cliente, CNPJ_VALIDO);
        criarPropostaAprovadaComContrato();
        sincronizar(admin);
        String oportunidadeId = idPrimeiraOportunidade(cliente);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses")
                .then()
                .statusCode(201);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses/me")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("ATIVO"))
                .body("oportunidadeId", org.hamcrest.Matchers.equalTo(oportunidadeId))
                .body("id", org.hamcrest.Matchers.notNullValue())
                .body("dataCriacao", org.hamcrest.Matchers.notNullValue())
                .body("$", org.hamcrest.Matchers.aMapWithSize(4));
    }

    @Test
    void semInteresseAtivoRetorna404Neutro() {
        Autenticado admin = criarAdminELogar();
        Autenticado cliente = criarClienteELogar();
        cadastrarCredoraElegivel(cliente, CNPJ_VALIDO);
        criarPropostaAprovadaComContrato();
        sincronizar(admin);
        String oportunidadeId = idPrimeiraOportunidade(cliente);

        // credora existe, mas nunca registrou interesse nesta oportunidade
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses/me")
                .then()
                .statusCode(404)
                .body("message", org.hamcrest.Matchers.equalTo(INTERESSE_404_MSG));
    }

    @Test
    void semCredoraRetorna404NeutroNoInteresseAtivo() {
        Autenticado cliente = criarClienteELogar();

        // usuario sem credora: MESMO corpo do caso "sem interesse" (nao vaza usuarioId nem
        // permite distinguir sem-credora de sem-interesse)
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/credores/oportunidades/" + UUID.randomUUID() + "/interesses/me")
                .then()
                .statusCode(404)
                .body("message", org.hamcrest.Matchers.equalTo(INTERESSE_404_MSG));
    }

    @Test
    void getInteresseAtivoNaoAlteraEstadoECicloRefleteCancelamento() {
        Autenticado admin = criarAdminELogar();
        Autenticado cliente = criarClienteELogar();
        cadastrarCredoraElegivel(cliente, CNPJ_VALIDO);
        criarPropostaAprovadaComContrato();
        sincronizar(admin);
        String oportunidadeId = idPrimeiraOportunidade(cliente);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .post("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses")
                .then()
                .statusCode(201);

        // ler duas vezes: 200 ATIVO, leitura idempotente e sem mutacao
        for (int i = 0; i < 2; i++) {
            RestAssured.given()
                    .header("Authorization", "Bearer " + cliente.token())
                    .when()
                    .get("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses/me")
                    .then()
                    .statusCode(200)
                    .body("status", org.hamcrest.Matchers.equalTo("ATIVO"));
        }

        // GET nao gera auditoria: apenas o registro emitiu evento (== 1)
        pollUntilAsserted(() -> assertThat(auditLogRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        cliente.id(), TipoEventoSeguranca.CREDORA_INTERESSE_REGISTRADO))
                .hasSize(1));

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .delete("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses/me")
                .then()
                .statusCode(204);

        // apos cancelar, o filtro JPA por ATIVO nao retorna o interesse CANCELADO
        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses/me")
                .then()
                .statusCode(404);
    }

    @Test
    void interesseAtivoDeOutraCredoraNaoVisivel() {
        Autenticado admin = criarAdminELogar();
        Autenticado dono = criarClienteELogar();
        Autenticado outro = criarClienteELogar();
        cadastrarCredoraElegivel(dono, CNPJ_VALIDO);
        cadastrarCredoraElegivel(outro, "11444777000161");
        criarPropostaAprovadaComContrato();
        sincronizar(admin);
        String oportunidadeId = idPrimeiraOportunidade(dono);

        // dono registra interesse
        RestAssured.given()
                .header("Authorization", "Bearer " + dono.token())
                .when()
                .post("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses")
                .then()
                .statusCode(201);

        // outro tem credora propria, mas nenhum interesse: 404 (escopo por credora, nao por oportunidade)
        RestAssured.given()
                .header("Authorization", "Bearer " + outro.token())
                .when()
                .get("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses/me")
                .then()
                .statusCode(404);

        // dono continua enxergando o proprio interesse
        RestAssured.given()
                .header("Authorization", "Bearer " + dono.token())
                .when()
                .get("/api/v1/credores/oportunidades/" + oportunidadeId + "/interesses/me")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("ATIVO"));
    }
}
