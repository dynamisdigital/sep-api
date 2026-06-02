package com.dynamis.sep_api.pix.web;

import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
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
import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixReferenciaRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixWebhookEventRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E do recebimento Pix (Sprint 21 Task 21.6): contrato ASSINADO -> agenda/parcela -> gerar
 * referencia Pix -> webhook RECEBIMENTO_PIX com txid/endToEndId/valor exato -> recebimento CONCILIADO
 * -> parcela PAGA -> Recebimento de cobranca (meioPagamento PIX) + movimentacao escrow -> replay nao
 * duplica. Usa FakePixProvider (default em test).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PixRecebimentoConciliacaoIT {

    private static final String WEBHOOK_SECRET = "dev-pix-webhook-secret-change-me";
    private static final String WEBHOOK_URL = "/api/v1/webhooks/celcoin/pix";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.pix.provider", () -> "fake");
        registry.add("app.webhooks.secrets.celcoin-pix", () -> WEBHOOK_SECRET);
        registry.add("app.cobranca.auto-geracao-pos-assinatura", () -> "true");
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

    @Autowired
    AgendaPagamentoRepository agendaRepository;

    @Autowired
    ParcelaCobrancaRepository parcelaRepository;

    @Autowired
    RecebimentoRepository recebimentoCobrancaRepository;

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
    PixReferenciaRecebimentoRepository referenciaRepository;

    @Autowired
    PixRecebimentoRepository pixRecebimentoRepository;

    @Autowired
    PixWebhookEventRepository webhookEventRepository;

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
            throw new IllegalStateException("PixRecebimentoConciliacaoIT deve rodar apenas no banco sep_test: " + url);
        }
        pixRecebimentoRepository.deleteAll();
        referenciaRepository.deleteAll();
        webhookEventRepository.deleteAll();
        renegociacaoRepository.deleteAll();
        eventoCobrancaRepository.deleteAll();
        recebimentoCobrancaRepository.deleteAll();
        movimentacaoEscrowRepository.deleteAll();
        walletRepository.deleteAll();
        contaEscrowRepository.deleteAll();
        agendaRepository.deleteAll();
        contratoRepository.deleteAll();
        propostaRepository.deleteAll();
        onboardingRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    private String loginFinanceiro() {
        String email = "fin-" + UUID.randomUUID().toString().substring(0, 8) + "@sep.test";
        String senha = "senha-passphrase-segura";
        usuarioRepository.saveAndFlush(Usuario.criar(email, passwordEncoder.encode(senha), Role.FINANCEIRO));
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + senha + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    private UUID seedParcelaPendente() {
        UUID tomadorId = usuarioRepository
                .saveAndFlush(Usuario.criar(
                        "tom-" + UUID.randomUUID().toString().substring(0, 8) + "@sep.test",
                        passwordEncoder.encode("senha-passphrase-segura"),
                        Role.CLIENTE))
                .getId();
        SolicitacaoOnboarding s = SolicitacaoOnboarding.criarPessoa(
                tomadorId, new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1));
        UUID onbId = onboardingRepository.saveAndFlush(s).getId();
        PropostaCredito p = PropostaCredito.criar(tomadorId, onbId, TipoOperacao.CAPITAL_GIRO, Money.brl("12000"), 12);
        propostaRepository.saveAndFlush(p);
        Contrato contrato = Contrato.criar(p.getId(), tomadorId, TipoContrato.MUTUO);
        contrato.adicionarVersao("conteudo", "0".repeat(64));
        contrato.marcarAceito();
        contrato.marcarEmAssinatura();
        contrato.marcarAssinado();
        contratoRepository.saveAndFlush(contrato);

        UUID contratoId = contrato.getId();
        new TransactionTemplate(txManager).execute(status -> {
            eventPublisher.publishEvent(new ContratoAssinadoEvent(
                    contratoId,
                    p.getId(),
                    tomadorId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "fake",
                    "env-" + contratoId,
                    "abcdef",
                    OffsetDateTime.now()));
            return null;
        });
        pollUntil(() -> agendaRepository.existsByContratoIdAndAtivaTrue(contratoId), "agenda criada");
        return parcelaRepository
                .findByAgenda_ContratoIdOrderByNumeroAsc(contratoId)
                .get(0)
                .getId();
    }

    @Test
    void recebimentoPixExato_conciliaParcelaEEscrow_replayNaoDuplica() {
        String token = loginFinanceiro();
        UUID parcelaId = seedParcelaPendente();

        // 1) Gerar referencia Pix para a parcela
        io.restassured.response.Response ref = RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"parcelaId\":\"" + parcelaId + "\"}")
                .when()
                .post("/api/v1/pix/recebimentos/referencias")
                .then()
                .statusCode(201)
                .extract()
                .response();
        String txid = ref.path("txid");
        BigDecimal valor = new BigDecimal(ref.path("valorEsperado").toString());

        // 2) Webhook RECEBIMENTO_PIX com txid + endToEndId + valor exato
        String e2e = "E2E-" + UUID.randomUUID();
        String eventId = "evt-" + UUID.randomUUID();
        String payload = "{\"event_id\":\"" + eventId + "\",\"event_type\":\"pix.received\",\"end_to_end_id\":\"" + e2e
                + "\",\"amount\":" + valor.toPlainString() + ",\"txid\":\"" + txid + "\"}";
        postWebhook(payload);

        // 3) Recebimento conciliado + parcela paga
        pollUntil(
                () -> pixRecebimentoRepository
                                .findByEndToEndId(e2e)
                                .map(PixRecebimento::getStatus)
                                .orElse(null)
                        == StatusPixRecebimento.CONCILIADO,
                "recebimento CONCILIADO");
        PixRecebimento recebimento =
                pixRecebimentoRepository.findByEndToEndId(e2e).orElseThrow();
        assertThat(recebimento.getParcelaId()).isEqualTo(parcelaId);
        assertThat(recebimento.getRecebimentoCobrancaId()).isNotNull();
        assertThat(parcelaRepository.findById(parcelaId).orElseThrow().getStatus())
                .isEqualTo(StatusParcela.PAGA);
        assertThat(referenciaRepository.findByTxid(txid).orElseThrow().getStatus())
                .isEqualTo(StatusPixReferenciaRecebimento.PAGA);

        // 4) Cobranca registrou Recebimento PIX + escrow uma vez
        long recebimentosPix = recebimentoCobrancaRepository.findAll().stream()
                .filter(r -> "PIX".equals(r.getMeioPagamento()))
                .count();
        assertThat(recebimentosPix).isEqualTo(1);
        long movimentacoes = movimentacaoEscrowRepository.findAll().size();
        assertThat(movimentacoes).isEqualTo(1);

        // 5) Replay do mesmo webhook (mesmo event_id) nao duplica
        postWebhook(payload);
        assertThat(recebimentoCobrancaRepository.findAll().stream()
                        .filter(r -> "PIX".equals(r.getMeioPagamento()))
                        .count())
                .isEqualTo(1);
        assertThat(movimentacaoEscrowRepository.findAll()).hasSize(1);
    }

    private void postWebhook(String payload) {
        RestAssured.given()
                .header("X-Webhook-Signature", computeHmac(payload))
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(WEBHOOK_URL)
                .then()
                .statusCode(202);
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

    private static String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
