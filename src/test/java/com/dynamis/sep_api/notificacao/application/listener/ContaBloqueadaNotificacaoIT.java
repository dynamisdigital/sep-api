package com.dynamis.sep_api.notificacao.application.listener;

import com.dynamis.sep_api.identity.domain.event.ContaBloqueadaEvent;
import com.dynamis.sep_api.notificacao.application.port.out.EnvioEmailPort;
import com.dynamis.sep_api.notificacao.application.port.out.dto.ResultadoEnvioEmail;
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
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-mail de lockout pelo modulo de notificacao, com transacoes e banco reais (Sprint 38 Task 38.5).
 *
 * <p>O provider de e-mail e simulado para poder falhar. O caminho feliz com o adapter de log real
 * esta no {@code LockoutLoginIT}. Aqui: falha do provider nao desfaz bloqueio nem audit e nao muda a
 * resposta do login; reavaliar o mesmo bloqueio nao duplica; bloqueio posterior notifica; bloqueio que
 * nao comitou nao notifica.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ContaBloqueadaNotificacaoIT {

    private static final String SENHA = "senha-passphrase-segura";
    private static final String SENHA_ERRADA = "senha-errada-mas-longa-o-suficiente";

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

    @MockitoBean
    EnvioEmailPort envioEmailPort;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    AuditLogSegurancaRepository auditRepository;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment environment;

    private String username;
    private UUID usuarioId;

    @BeforeEach
    void setup() {
        if (!environment.getProperty("spring.datasource.url", "").contains("sep_test")) {
            throw new IllegalStateException("ContaBloqueadaNotificacaoIT deve rodar apenas no banco sep_test");
        }
        RestAssured.port = port;
        username = "bloqueio-" + UUID.randomUUID().toString().substring(0, 8) + "@sep.test";
        usuarioId = usuarioRepository
                .saveAndFlush(Usuario.criar(username, passwordEncoder.encode(SENHA), Role.CLIENTE))
                .getId();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from notificacao where usuario_id = ?", usuarioId);
        usuarioRepository.deleteById(usuarioId);
    }

    private int tentarLogin() {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"" + SENHA_ERRADA + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .statusCode();
    }

    private List<Map<String, Object>> notificacoesDoUsuario() {
        return jdbc.queryForList(
                "select situacao, motivo_falha, origem_id from notificacao where usuario_id = ? order by criada_em",
                usuarioId);
    }

    private void publicarComitado(OffsetDateTime bloqueadaEm) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status ->
                        eventPublisher.publishEvent(new ContaBloqueadaEvent(usuarioId, username, bloqueadaEm, 30)));
    }

    @Test
    void providerLancando_bloqueioEAuditPersistem_loginNaoMudaEAFalhaFicaNoHistorico() {
        when(envioEmailPort.enviar(any())).thenThrow(new IllegalStateException("SMTP recusou " + username));

        for (int i = 1; i <= 5; i++) {
            assertThat(tentarLogin())
                    .as("tentativa %d: a falha do e-mail nao pode virar 500", i)
                    .isEqualTo(401);
        }
        assertThat(tentarLogin())
                .as("o bloqueio comitou mesmo com o e-mail falhando")
                .isEqualTo(423);

        assertThat(auditRepository.findByUsuarioIdAndTipoOrderByDataEventoDesc(usuarioId, TipoEventoSeguranca.LOCKOUT))
                .as("o audit do bloqueio sobrevive a falha do e-mail")
                .hasSize(1);
        assertThat(notificacoesDoUsuario())
                .as("a tentativa fica no historico como FALHOU, com o nome da classe e sem a mensagem")
                .singleElement()
                .satisfies(linha -> {
                    assertThat(linha).containsEntry("situacao", "FALHOU");
                    assertThat(linha).containsEntry("motivo_falha", "java.lang.IllegalStateException");
                });
    }

    @Test
    void mesmoBloqueioReavaliado_umaNotificacao_eBloqueioPosterior_outra() {
        when(envioEmailPort.enviar(any())).thenReturn(ResultadoEnvioEmail.SIMULADO);
        OffsetDateTime primeiro = OffsetDateTime.of(2026, 9, 14, 10, 0, 0, 0, ZoneOffset.UTC);

        publicarComitado(primeiro);
        publicarComitado(primeiro.withOffsetSameInstant(ZoneOffset.ofHours(-3)));

        assertThat(notificacoesDoUsuario())
                .as("o mesmo instante e o mesmo bloqueio")
                .hasSize(1);
        verify(envioEmailPort, times(1)).enviar(any());

        publicarComitado(primeiro.plusMinutes(45));

        assertThat(notificacoesDoUsuario())
                .extracting(linha -> linha.get("origem_id"))
                .containsExactly("2026-09-14T10:00:00Z", "2026-09-14T10:45:00Z");
        verify(envioEmailPort, times(2)).enviar(any());
    }

    @Test
    void bloqueioQueNaoComitou_naoNotifica() {
        when(envioEmailPort.enviar(any())).thenReturn(ResultadoEnvioEmail.SIMULADO);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new ContaBloqueadaEvent(usuarioId, username, OffsetDateTime.now(), 30));
            status.setRollbackOnly();
        });

        assertThat(notificacoesDoUsuario()).isEmpty();
        verify(envioEmailPort, never()).enviar(any());
    }
}
