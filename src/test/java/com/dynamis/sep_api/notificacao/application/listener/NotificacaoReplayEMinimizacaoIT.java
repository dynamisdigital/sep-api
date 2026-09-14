package com.dynamis.sep_api.notificacao.application.listener;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dynamis.sep_api.identity.domain.event.ContaBloqueadaEvent;
import com.dynamis.sep_api.notificacao.application.port.out.EnvioEmailPort;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaConcluidaEvent;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Aceites 4 e 6 da spec 038 (Sprint 38 Task 38.7), pelo caminho de producao: evento publicado no
 * {@code ApplicationEventPublisher}, em transacao confirmada, ate o banco {@code sep_test}.
 *
 * <p><b>Replay</b>: o mesmo evento duas vezes, ou em paralelo, gera uma linha e um incremento no
 * contador. A guarda do {@code SincronizadorStatusTransferencia} (nao republicar status terminal) nao
 * participa — o evento e publicado direto, para provar a deduplicacao do modulo e nao a do Pix.
 *
 * <p><b>Minimizacao</b>: dado sensivel posto onde ele pode entrar — {@code externalId}, username,
 * mensagem de excecao — nao aparece no banco, na resposta HTTP nem nos logs do modulo. A checagem e
 * por valor, nao por nome de coluna: blacklist de nomes nao pega dado dentro de texto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificacaoReplayEMinimizacaoIT {

    private static final String SENHA = "senha-passphrase-segura";
    private static final String CPF = "529.982.247-25";
    private static final String CPF_SEM_MASCARA = "52998224725";
    private static final String CNPJ = "11.222.333/0001-81";
    private static final String CHAVE_PIX = "fulano.beneficiario@pix.example";
    private static final String SEGREDO = "Bearer eyJhbGciOiJIUzI1NiJ9.segredo";
    private static final String EXTERNAL_ID_SENSIVEL =
            "E2E|" + CPF + "|" + CNPJ + "|" + CHAVE_PIX + "|" + SEGREDO + "|" + CPF_SEM_MASCARA;

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

    @MockitoSpyBean
    EnvioEmailPort envioEmailPort;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment environment;

    private final List<UUID> usuarios = new ArrayList<>();
    private final Logger loggerDoModulo = (Logger) LoggerFactory.getLogger("com.dynamis.sep_api.notificacao");
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    private record Conta(UUID id, String username, String token) {}

    @BeforeEach
    void setup() {
        if (!environment.getProperty("spring.datasource.url", "").contains("sep_test")) {
            throw new IllegalStateException("NotificacaoReplayEMinimizacaoIT deve rodar apenas no banco sep_test");
        }
        RestAssured.port = port;
        logs.start();
        loggerDoModulo.addAppender(logs);
    }

    @AfterEach
    void cleanup() {
        loggerDoModulo.detachAppender(logs);
        logs.stop();
        for (UUID usuario : usuarios) {
            jdbc.update("delete from notificacao where usuario_id = ?", usuario);
            usuarioRepository.deleteById(usuario);
        }
    }

    private Conta criarConta(String prefixo) {
        String username = prefixo + "-" + UUID.randomUUID().toString().substring(0, 8) + "@sep.test";
        UUID id = usuarioRepository
                .saveAndFlush(Usuario.criar(username, passwordEncoder.encode(SENHA), Role.CLIENTE))
                .getId();
        usuarios.add(id);
        String token = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"" + SENHA + "\"}")
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        return new Conta(id, username, token);
    }

    private void publicarComitado(Object evento) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(evento));
    }

    private long linhas(UUID usuarioId) {
        return jdbc.queryForObject("select count(*) from notificacao where usuario_id = ?", Long.class, usuarioId);
    }

    private int naoLidasPelaApi(Conta conta) {
        return RestAssured.given()
                .header("Authorization", "Bearer " + conta.token())
                .get("/api/v1/notificacoes/nao-lidas/contagem")
                .then()
                .statusCode(200)
                .extract()
                .path("naoLidas");
    }

    // ================= replay =================

    @Test
    void mesmoEventoPixDuasVezes_umaLinhaEUmIncrementoNoContador() {
        Conta tomador = criarConta("replay");
        PixTransferenciaConcluidaEvent evento =
                new PixTransferenciaConcluidaEvent(UUID.randomUUID(), UUID.randomUUID(), tomador.id(), "ext-1");

        publicarComitado(evento);
        publicarComitado(evento);

        assertThat(linhas(tomador.id())).isEqualTo(1);
        assertThat(naoLidasPelaApi(tomador)).isEqualTo(1);
    }

    /**
     * Duas publicacoes simultaneas, e nao oito: cada uma segura a conexao da transacao de origem durante
     * o {@code AFTER_COMMIT} e pede outra para o {@code REQUIRES_NEW} do listener. Com o pool de 5 do
     * perfil {@code test}, oito threads esgotam o pool — medido na Task 38.7, e o mesmo acontece so com o
     * listener de audit do Pix (Sprint 20), entao o limite e anterior a esta sprint. A corrida na
     * constraint com oito threads esta provada sem transacao externa no
     * {@code NotificacaoPersistenceAdapterTest}.
     */
    @Test
    void mesmoEventoPixEmParalelo_umaLinha() throws Exception {
        Conta tomador = criarConta("paralelo");
        PixTransferenciaConcluidaEvent evento =
                new PixTransferenciaConcluidaEvent(UUID.randomUUID(), UUID.randomUUID(), tomador.id(), "ext-2");
        int concorrentes = 2;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(concorrentes);
        try {
            List<Future<?>> publicacoes = new ArrayList<>();
            for (int i = 0; i < concorrentes; i++) {
                publicacoes.add(executor.submit(() -> {
                    largada.await();
                    publicarComitado(evento);
                    return null;
                }));
            }
            largada.countDown();
            for (Future<?> publicacao : publicacoes) {
                publicacao.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(linhas(tomador.id())).isEqualTo(1);
        assertThat(naoLidasPelaApi(tomador)).isEqualTo(1);
    }

    @Test
    void deduplicacaoNaoSuprimeNotificacaoLegitima() {
        Conta a = criarConta("legitima-a");
        Conta b = criarConta("legitima-b");
        UUID transferencia = UUID.randomUUID();

        publicarComitado(new PixTransferenciaConcluidaEvent(transferencia, UUID.randomUUID(), a.id(), "ext-3"));
        publicarComitado(new PixTransferenciaConcluidaEvent(transferencia, UUID.randomUUID(), b.id(), "ext-3"));
        publicarComitado(new PixTransferenciaConcluidaEvent(UUID.randomUUID(), UUID.randomUUID(), a.id(), "ext-4"));

        assertThat(linhas(a.id())).as("mesmo destinatario, outra origem").isEqualTo(2);
        assertThat(linhas(b.id())).as("mesma origem, outro destinatario").isEqualTo(1);
        assertThat(naoLidasPelaApi(a)).isEqualTo(2);
        assertThat(naoLidasPelaApi(b)).isEqualTo(1);
    }

    // ================= minimizacao =================

    private List<String> sensiveis(String... extras) {
        List<String> valores = new ArrayList<>(List.of(CPF, CPF_SEM_MASCARA, CNPJ, CHAVE_PIX, SEGREDO, "eyJhbGci"));
        valores.addAll(List.of(extras));
        return valores;
    }

    private String linhasComoJson(UUID usuarioId) {
        return String.join(
                "\n",
                jdbc.queryForList(
                        "select row_to_json(n)::text from notificacao n where usuario_id = ?",
                        String.class,
                        usuarioId));
    }

    private String logsDoModulo() {
        StringBuilder texto = new StringBuilder();
        for (ILoggingEvent evento : logs.list) {
            texto.append(evento.getFormattedMessage()).append(evento.getKeyValuePairs());
            if (evento.getThrowableProxy() != null) {
                texto.append(evento.getThrowableProxy().getMessage());
            }
            texto.append('\n');
        }
        return texto.toString();
    }

    @Test
    void pixConcluido_gravaSoOPermitido_eNadaSensivelChegaAoBancoHttpOuLog() {
        Conta tomador = criarConta("payload-pix");
        UUID transferencia = UUID.randomUUID();
        UUID contrato = UUID.randomUUID();

        publicarComitado(
                new PixTransferenciaConcluidaEvent(transferencia, contrato, tomador.id(), EXTERNAL_ID_SENSIVEL));

        Map<String, Object> linha = jdbc.queryForMap(
                "select tipo, canal, situacao, origem_id, titulo, mensagem, referencia_tipo, referencia_id, motivo_falha"
                        + " from notificacao where usuario_id = ?",
                tomador.id());
        assertThat(linha)
                .as("allowlist do tipo DESEMBOLSO_PIX_CONCLUIDO: texto fixo, origem e contrato")
                .containsEntry("tipo", "DESEMBOLSO_PIX_CONCLUIDO")
                .containsEntry("canal", "IN_APP")
                .containsEntry("situacao", "DISPONIVEL")
                .containsEntry("origem_id", transferencia.toString())
                .containsEntry("titulo", DesembolsoPixConcluidoListener.TITULO)
                .containsEntry("mensagem", DesembolsoPixConcluidoListener.MENSAGEM)
                .containsEntry("referencia_tipo", "CONTRATO")
                .containsEntry("referencia_id", contrato)
                .containsEntry("motivo_falha", null);

        String http = RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .get("/api/v1/notificacoes")
                .then()
                .statusCode(200)
                .extract()
                .asString();
        for (String sensivel : sensiveis("ext-", "E2E|")) {
            assertThat(linhasComoJson(tomador.id())).as("banco").doesNotContain(sensivel);
            assertThat(http).as("HTTP").doesNotContain(sensivel);
            assertThat(logsDoModulo()).as("logs do modulo").doesNotContain(sensivel);
        }
    }

    @Test
    void contaBloqueada_naoGravaOEnderecoNemMensagemDeExcecaoComDadoSensivel() {
        Conta conta = criarConta("payload-cpf-" + CPF_SEM_MASCARA);
        doThrow(new IllegalStateException("SMTP recusou " + conta.username() + " cpf " + CPF + " " + SEGREDO))
                .when(envioEmailPort)
                .enviar(any());
        OffsetDateTime bloqueadaEm = OffsetDateTime.of(2026, 9, 14, 13, 0, 0, 0, ZoneOffset.UTC);

        publicarComitado(new ContaBloqueadaEvent(conta.id(), conta.username(), bloqueadaEm, 30));

        Map<String, Object> linha = jdbc.queryForMap(
                "select tipo, canal, situacao, origem_id, titulo, referencia_tipo, referencia_id, motivo_falha"
                        + " from notificacao where usuario_id = ?",
                conta.id());
        assertThat(linha)
                .as("allowlist do tipo CONTA_BLOQUEADA: texto fixo, origem pelo instante, sem referencia")
                .containsEntry("tipo", "CONTA_BLOQUEADA")
                .containsEntry("canal", "EMAIL")
                .containsEntry("situacao", "FALHOU")
                .containsEntry("origem_id", "2026-09-14T13:00:00Z")
                .containsEntry("titulo", ContaBloqueadaListener.ASSUNTO)
                .containsEntry("referencia_tipo", null)
                .containsEntry("referencia_id", null)
                .containsEntry("motivo_falha", "java.lang.IllegalStateException");
        for (String sensivel : sensiveis(conta.username(), "SMTP recusou")) {
            assertThat(linhasComoJson(conta.id())).as("banco").doesNotContain(sensivel);
            assertThat(logsDoModulo()).as("logs do modulo").doesNotContain(sensivel);
        }
    }
}
