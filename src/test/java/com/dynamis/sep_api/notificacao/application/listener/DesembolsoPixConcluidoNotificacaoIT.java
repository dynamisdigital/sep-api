package com.dynamis.sep_api.notificacao.application.listener;

import com.dynamis.sep_api.notificacao.application.port.out.EnvioEmailPort;
import com.dynamis.sep_api.notificacao.application.port.out.NotificacaoPort;
import com.dynamis.sep_api.pix.application.dto.ConsultarStatusDesembolsoPixCommand;
import com.dynamis.sep_api.pix.application.usecase.ConsultarStatusDesembolsoPixUseCase;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.pix.infrastructure.adapter.fake.FakePixProvider;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pix concluido vira aviso na central do tomador, com transicao real (Sprint 38 Task 38.6): o
 * {@code ConsultarStatusDesembolsoPixUseCase} consulta o {@code FakePixProvider}, o sincronizador
 * conclui a transferencia e publica o evento, e o listener grava depois do commit. Banco
 * {@code sep_test} e JWT reais.
 *
 * <p>{@code IN_APP} nao tem provider externo, entao a falha provocada e a da <b>porta de gravacao</b>
 * ({@link NotificacaoPort}). Falha de provider de e-mail e outra coisa, coberta na Task 38.5.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DesembolsoPixConcluidoNotificacaoIT {

    private static final String SENHA = "senha-passphrase-segura";

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

    @MockitoSpyBean
    NotificacaoPort notificacaoPort;

    @MockitoSpyBean
    EnvioEmailPort envioEmailPort;

    @Autowired
    ConsultarStatusDesembolsoPixUseCase consultarStatus;

    @Autowired
    FakePixProvider fakePixProvider;

    @Autowired
    PixTransferenciaRepository transferenciaRepository;

    @Autowired
    AuditLogSegurancaRepository auditRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment environment;

    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> transferencias = new ArrayList<>();

    private record Tomador(UUID id, String token) {}

    @BeforeEach
    void setup() {
        if (!environment.getProperty("spring.datasource.url", "").contains("sep_test")) {
            throw new IllegalStateException("DesembolsoPixConcluidoNotificacaoIT deve rodar apenas no banco sep_test");
        }
        RestAssured.port = port;
        fakePixProvider.reset();
    }

    @AfterEach
    void cleanup() {
        fakePixProvider.reset();
        transferencias.forEach(transferenciaRepository::deleteById);
        for (UUID usuario : usuarios) {
            jdbc.update("delete from notificacao where usuario_id = ?", usuario);
            usuarioRepository.deleteById(usuario);
        }
    }

    private Tomador criarTomador() {
        String email = "desembolso-" + UUID.randomUUID().toString().substring(0, 8) + "@sep.test";
        UUID id = usuarioRepository
                .saveAndFlush(Usuario.criar(email, passwordEncoder.encode(SENHA), Role.CLIENTE))
                .getId();
        usuarios.add(id);
        String token = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + SENHA + "\"}")
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        return new Tomador(id, token);
    }

    /** Transferencia ja solicitada ao provider, aguardando a consulta que a conclui. */
    private PixTransferencia transferenciaSolicitada(UUID tomadorId) {
        String chave = "idem-notificacao-" + UUID.randomUUID();
        PixTransferencia transferencia = PixTransferencia.criarDesembolso(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tomadorId,
                new BigDecimal("1500.00"),
                "a".repeat(64),
                "***.456.789-**",
                chave,
                "corr-notificacao");
        transferencia.marcarSolicitada("ext-" + chave);
        transferencias.add(transferencia.getId());
        return transferenciaRepository.saveAndFlush(transferencia);
    }

    private void concluirPeloProvider(UUID transferenciaId) {
        consultarStatus.executar(new ConsultarStatusDesembolsoPixCommand(transferenciaId, "corr-notificacao", true));
    }

    private StatusPixTransferencia statusNoBanco(UUID transferenciaId) {
        return StatusPixTransferencia.valueOf(jdbc.queryForObject(
                "select status from pix_transferencia where id = ?", String.class, transferenciaId));
    }

    private List<Map<String, Object>> notificacoesDe(UUID usuarioId) {
        return jdbc.queryForList(
                "select tipo, canal, situacao, origem_id, referencia_tipo, referencia_id, lida_em"
                        + " from notificacao where usuario_id = ?",
                usuarioId);
    }

    @Test
    void pixConcluido_viraAvisoNaoLidoNaCentralDoTomador_semEnviarEmail() {
        Tomador tomador = criarTomador();
        Tomador outro = criarTomador();
        PixTransferencia transferencia = transferenciaSolicitada(tomador.id());

        concluirPeloProvider(transferencia.getId());

        assertThat(statusNoBanco(transferencia.getId())).isEqualTo(StatusPixTransferencia.CONCLUIDA);
        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .get("/api/v1/notificacoes")
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].tipo", equalTo("DESEMBOLSO_PIX_CONCLUIDO"))
                .body("content[0].titulo", equalTo("Desembolso concluido"))
                .body("content[0].lidaEm", nullValue())
                .body("content[0].referencia.tipo", equalTo("CONTRATO"))
                .body(
                        "content[0].referencia.id",
                        equalTo(transferencia.getContratoId().toString()));
        RestAssured.given()
                .header("Authorization", "Bearer " + tomador.token())
                .get("/api/v1/notificacoes/nao-lidas/contagem")
                .then()
                .body("naoLidas", equalTo(1));
        RestAssured.given()
                .header("Authorization", "Bearer " + outro.token())
                .get("/api/v1/notificacoes/nao-lidas/contagem")
                .then()
                .body("naoLidas", equalTo(0));

        assertThat(notificacoesDe(tomador.id())).singleElement().satisfies(linha -> assertThat(linha)
                .containsEntry("canal", "IN_APP")
                .containsEntry("situacao", "DISPONIVEL")
                .containsEntry("origem_id", transferencia.getId().toString()));
        verify(envioEmailPort, never()).enviar(any());
    }

    @Test
    void conclusaoRevertida_naoNotifica() {
        Tomador tomador = criarTomador();
        PixTransferencia transferencia = transferenciaSolicitada(tomador.id());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            concluirPeloProvider(transferencia.getId());
            status.setRollbackOnly();
        });

        assertThat(statusNoBanco(transferencia.getId())).isEqualTo(StatusPixTransferencia.SOLICITADA);
        assertThat(notificacoesDe(tomador.id())).isEmpty();
        verify(notificacaoPort, never()).registrarSeInedita(any());
    }

    @Test
    void falhaAoGravarONotificacao_desembolsoContinuaConcluidoEAuditado() {
        Tomador tomador = criarTomador();
        PixTransferencia transferencia = transferenciaSolicitada(tomador.id());
        doThrow(new DataAccessResourceFailureException("banco de notificacao indisponivel"))
                .when(notificacaoPort)
                .registrarSeInedita(any());

        concluirPeloProvider(transferencia.getId());

        assertThat(statusNoBanco(transferencia.getId()))
                .as("a falha da notificacao nao pode desfazer o desembolso")
                .isEqualTo(StatusPixTransferencia.CONCLUIDA);
        assertThat(auditRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(
                        tomador.id(), TipoEventoSeguranca.PIX_TRANSFERENCIA_CONCLUIDA))
                .as("o audit do desembolso e outro listener, e segue gravado")
                .hasSize(1);
        verify(notificacaoPort).registrarSeInedita(any());
        assertThat(notificacoesDe(tomador.id())).isEmpty();
    }
}
