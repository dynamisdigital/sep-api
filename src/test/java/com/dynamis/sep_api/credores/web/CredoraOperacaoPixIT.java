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
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
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
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

/**
 * E2E backend da leitura Pix owner-scoped de uma operacao da carteira da credora (Sprint 26 — Gate
 * P3). Sobe Spring Boot completo + Postgres local ({@code sep_test}) e exercita seguranca real,
 * provando ownership por credora (sem role CREDORA), 404 neutro anti-enumeracao e minimizacao do
 * JSON (sem tomador, contrato, chave ou IDs internos).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CredoraOperacaoPixIT {

    private static final String CNPJ_A = "11222333000181";
    private static final String CNPJ_B = "11444777000161";

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
    PropostaCreditoRepository propostaRepository;

    @Autowired
    ContratoRepository contratoRepository;

    @Autowired
    PixTransferenciaRepository transferenciaRepository;

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
            throw new IllegalStateException("CredoraOperacaoPixIT deve rodar apenas no banco sep_test; URL: " + url);
        }
        transferenciaRepository.deleteAll();
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
    private final AtomicInteger keyCursor = new AtomicInteger();

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

    private ContratoTomador criarContratoParaCarteira() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String cpf = CPFS[cpfCursor.getAndIncrement() % CPFS.length];
        Usuario tomador = usuarioRepository.saveAndFlush(Usuario.criar(
                "tomador-" + suffix + "@sep.test", passwordEncoder.encode("senha-passphrase-segura"), Role.CLIENTE));
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
        c.marcarAssinado();
        UUID contratoId = contratoRepository.saveAndFlush(c).getId();
        return new ContratoTomador(contratoId, p.getId(), tomador.getId());
    }

    private String associar(Autenticado admin, UUID credoraId, UUID contratoId) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + admin.token())
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

    private void seedTransferencia(ContratoTomador ct, StatusPixTransferencia status, String valor) {
        String key = "idem-" + keyCursor.incrementAndGet();
        PixTransferencia t = PixTransferencia.criarDesembolso(
                ct.contratoId(),
                ct.propostaId(),
                ct.tomadorId(),
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

    private static final String PATH = "/api/v1/credores/carteira/{id}/pix";

    // ============== cenarios ==============

    @Test
    void ownerComPix_200LiquidadoMinimo() {
        Autenticado admin = criarELogar(Role.ADMIN);
        Autenticado credora = criarELogar(Role.CLIENTE);
        UUID credoraId = cadastrarCredoraElegivel(credora, CNPJ_A);
        ContratoTomador ct = criarContratoParaCarteira();
        seedTransferencia(ct, StatusPixTransferencia.CONCLUIDA, "1500.00");
        String operacaoId = associar(admin, credoraId, ct.contratoId());

        RestAssured.given()
                .header("Authorization", "Bearer " + credora.token())
                .when()
                .get(PATH, operacaoId)
                .then()
                .statusCode(200)
                .body("status", equalTo("LIQUIDADO"))
                .body("valor", equalTo(1500.00f))
                .body("$", hasKey("atualizadoEm"))
                .body("$", not(hasKey("tomadorId")))
                .body("$", not(hasKey("contratoId")))
                .body("$", not(hasKey("chaveDestinoMascara")))
                .body("$", not(hasKey("txid")))
                .body("$", not(hasKey("transferenciaId")))
                .body("$", not(hasKey("justificativa")));
    }

    @Test
    void operacaoDeOutraCredora_404() {
        Autenticado admin = criarELogar(Role.ADMIN);
        Autenticado dono = criarELogar(Role.CLIENTE);
        Autenticado outro = criarELogar(Role.CLIENTE);
        UUID credoraDono = cadastrarCredoraElegivel(dono, CNPJ_A);
        cadastrarCredoraElegivel(outro, CNPJ_B);
        ContratoTomador ct = criarContratoParaCarteira();
        seedTransferencia(ct, StatusPixTransferencia.CONCLUIDA, "1500.00");
        String operacaoId = associar(admin, credoraDono, ct.contratoId());

        RestAssured.given()
                .header("Authorization", "Bearer " + outro.token())
                .when()
                .get(PATH, operacaoId)
                .then()
                .statusCode(404);
    }

    @Test
    void usuarioSemCredora_404() {
        Autenticado cliente = criarELogar(Role.CLIENTE);

        RestAssured.given()
                .header("Authorization", "Bearer " + cliente.token())
                .when()
                .get(PATH, UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void operacaoInexistente_404() {
        Autenticado credora = criarELogar(Role.CLIENTE);
        cadastrarCredoraElegivel(credora, CNPJ_A);

        RestAssured.given()
                .header("Authorization", "Bearer " + credora.token())
                .when()
                .get(PATH, UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void operacaoSemDesembolsoPix_404() {
        Autenticado admin = criarELogar(Role.ADMIN);
        Autenticado credora = criarELogar(Role.CLIENTE);
        UUID credoraId = cadastrarCredoraElegivel(credora, CNPJ_A);
        ContratoTomador ct = criarContratoParaCarteira();
        String operacaoId = associar(admin, credoraId, ct.contratoId());

        RestAssured.given()
                .header("Authorization", "Bearer " + credora.token())
                .when()
                .get(PATH, operacaoId)
                .then()
                .statusCode(404);
    }
}
