package com.dynamis.sep_api.notificacao.web;

import com.dynamis.sep_api.notificacao.application.port.out.NotificacaoPort;
import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.Referencia;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.TipoReferencia;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Central de notificacoes ponta a ponta (Sprint 38 Task 38.4): Spring Boot completo em RANDOM_PORT,
 * JWT real e PostgreSQL ({@code sep_test}). Prova o aceite 3 da spec 038 — o usuario A nao enxerga nem
 * altera notificacao do usuario B em nenhum dos tres endpoints — conferindo o banco, nao so a resposta.
 *
 * <p>Apaga apenas o que cria: outras suites fazem {@code deleteAll()} de usuario no mesmo banco, e
 * notificacao restante travaria essa limpeza pela FK.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CentralNotificacoesIT {

    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 9, 14, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final String SENHA = "senha-passphrase-segura";

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    NotificacaoPort notificacaoPort;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment environment;

    private final List<UUID> usuariosCriados = new ArrayList<>();

    private record Conta(UUID id, String token) {}

    @BeforeEach
    void setup() {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.contains("sep_test")) {
            throw new IllegalStateException("CentralNotificacoesIT deve rodar apenas no banco sep_test; URL: " + url);
        }
        RestAssured.port = port;
    }

    @AfterEach
    void cleanup() {
        for (UUID usuario : usuariosCriados) {
            // login_attempt vira NULL e refresh_token cascateia; so notificacao trava a exclusao.
            jdbc.update("delete from notificacao where usuario_id = ?", usuario);
            usuarioRepository.deleteById(usuario);
        }
    }

    private Conta criarELogar() {
        String email = "central-" + UUID.randomUUID().toString().substring(0, 8) + "@sep.test";
        Usuario usuario =
                usuarioRepository.saveAndFlush(Usuario.criar(email, passwordEncoder.encode(SENHA), Role.CLIENTE));
        usuariosCriados.add(usuario.getId());
        String token = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"" + email + "\",\"password\":\"" + SENHA + "\"}")
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        return new Conta(usuario.getId(), token);
    }

    private Notificacao inApp(Conta conta, OffsetDateTime criadaEm) {
        Notificacao notificacao = Notificacao.disponibilizarInApp(
                conta.id(),
                new OrigemNotificacao(
                        TipoNotificacao.DESEMBOLSO_PIX_CONCLUIDO,
                        UUID.randomUUID().toString()),
                new ConteudoNotificacao(
                        "Desembolso concluido",
                        "A transferencia Pix do desembolso do seu contrato foi concluida.",
                        new Referencia(TipoReferencia.CONTRATO, UUID.randomUUID())),
                criadaEm);
        assertThat(notificacaoPort.registrarSeInedita(notificacao)).isTrue();
        return notificacao;
    }

    private Notificacao email(Conta conta) {
        Notificacao notificacao = Notificacao.registrarEmail(
                conta.id(),
                new OrigemNotificacao(
                        TipoNotificacao.CONTA_BLOQUEADA, BASE.toInstant().toString()),
                new ConteudoNotificacao("Conta SEP bloqueada", "Sua conta esta bloqueada.", null),
                BASE);
        assertThat(notificacaoPort.registrarSeInedita(notificacao)).isTrue();
        return notificacao;
    }

    private io.restassured.specification.RequestSpecification como(Conta conta) {
        return RestAssured.given().header("Authorization", "Bearer " + conta.token());
    }

    private String lidaEmNoBanco(UUID notificacaoId) {
        OffsetDateTime lidaEm = jdbc.queryForObject(
                "select lida_em from notificacao where id = ?", OffsetDateTime.class, notificacaoId);
        return lidaEm == null ? null : lidaEm.toInstant().toString();
    }

    @Test
    void contaANaoEnxergaNemAlteraNotificacaoDaContaB() {
        Conta a = criarELogar();
        Conta b = criarELogar();
        Notificacao antigaDeA = inApp(a, BASE);
        Notificacao recenteDeA = inApp(a, BASE.plusMinutes(5));
        Notificacao emailDeA = email(a);
        Notificacao deB = inApp(b, BASE.plusMinutes(10));

        como(a).get("/api/v1/notificacoes")
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(2))
                .body(
                        "content.id",
                        contains(
                                recenteDeA.getId().toString(), antigaDeA.getId().toString()))
                .body("content.id", not(hasItem(deB.getId().toString())))
                .body("content.id", not(hasItem(emailDeA.getId().toString())));
        como(a).get("/api/v1/notificacoes/nao-lidas/contagem")
                .then()
                .statusCode(200)
                .body("naoLidas", equalTo(2));
        como(b).get("/api/v1/notificacoes/nao-lidas/contagem")
                .then()
                .statusCode(200)
                .body("naoLidas", equalTo(1));

        como(a).post("/api/v1/notificacoes/{id}/leitura", deB.getId())
                .then()
                .statusCode(404)
                .body("codigo", equalTo("NTF-404-001"))
                .body("message", equalTo("Notificacao nao encontrada"));

        assertThat(lidaEmNoBanco(deB.getId()))
                .as("a tentativa de A nao pode ter marcado a notificacao de B")
                .isNull();
        como(b).get("/api/v1/notificacoes/nao-lidas/contagem").then().body("naoLidas", equalTo(1));
    }

    @Test
    void marcarLida_eIdempotenteEAtualizaOContador() {
        Conta a = criarELogar();
        Notificacao notificacao = inApp(a, BASE);
        inApp(a, BASE.plusMinutes(1));

        String primeiraLeitura = como(a).post("/api/v1/notificacoes/{id}/leitura", notificacao.getId())
                .then()
                .statusCode(200)
                .body("id", equalTo(notificacao.getId().toString()))
                .body("lidaEm", not(nullValue()))
                .extract()
                .path("lidaEm");
        como(a).get("/api/v1/notificacoes/nao-lidas/contagem").then().body("naoLidas", equalTo(1));

        como(a).post("/api/v1/notificacoes/{id}/leitura", notificacao.getId())
                .then()
                .statusCode(200)
                .body("lidaEm", equalTo(primeiraLeitura));
        como(a).get("/api/v1/notificacoes/nao-lidas/contagem").then().body("naoLidas", equalTo(1));

        assertThat(OffsetDateTime.parse(primeiraLeitura).toInstant().toString())
                .isEqualTo(lidaEmNoBanco(notificacao.getId()));
    }

    @Test
    void emailDoProprioUsuario_ficaForaDaCentral() {
        Conta a = criarELogar();
        Notificacao emailDeA = email(a);

        como(a).post("/api/v1/notificacoes/{id}/leitura", emailDeA.getId())
                .then()
                .statusCode(404)
                .body("codigo", equalTo("NTF-404-001"));
        como(a).get("/api/v1/notificacoes/nao-lidas/contagem").then().body("naoLidas", equalTo(0));
        assertThat(lidaEmNoBanco(emailDeA.getId())).isNull();
    }

    @Test
    void usuarioSemNotificacao_recebePaginaVaziaEContadorZero() {
        Conta a = criarELogar();

        como(a).get("/api/v1/notificacoes")
                .then()
                .statusCode(200)
                .body("content", hasSize(0))
                .body("totalElements", equalTo(0));
        como(a).get("/api/v1/notificacoes/nao-lidas/contagem")
                .then()
                .statusCode(200)
                .body("naoLidas", equalTo(0));
    }

    @Test
    void paginacaoForaDosLimites_400ComCodigo() {
        Conta a = criarELogar();

        como(a).queryParam("size", 101)
                .get("/api/v1/notificacoes")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("NTF-400-001"));
        como(a).queryParam("page", -1)
                .get("/api/v1/notificacoes")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("NTF-400-001"));
        como(a).queryParam("size", 0)
                .get("/api/v1/notificacoes")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("NTF-400-001"));
    }

    @Test
    void idInexistente404_eIdQueNaoEUuid400() {
        Conta a = criarELogar();

        como(a).post("/api/v1/notificacoes/{id}/leitura", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("codigo", equalTo("NTF-404-001"));
        como(a).post("/api/v1/notificacoes/{id}/leitura", "nao-eh-uuid").then().statusCode(400);
    }

    @Test
    void contratoOpenApi_coincideComARespostaReal() {
        Conta a = criarELogar();
        Notificacao notificacao = inApp(a, BASE);
        Map<String, Object> item =
                como(a).get("/api/v1/notificacoes").then().extract().path("content[0]");
        Map<String, Object> contagem = como(a).get("/api/v1/notificacoes/nao-lidas/contagem")
                .then()
                .extract()
                .path("");
        io.restassured.path.json.JsonPath documento = RestAssured.given()
                .get("/v3/api-docs")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();

        Map<String, Object> schema = documento.getMap("components.schemas.NotificacaoResponse.properties");
        assertThat(item.keySet())
                .as("toda chave da resposta real esta documentada, e vice-versa")
                .containsExactlyInAnyOrderElementsOf(schema.keySet());
        assertThat(documento.getList("components.schemas.NotificacaoResponse.required", String.class))
                .as("so e obrigatorio o que nunca chega nulo")
                .containsExactlyInAnyOrder("id", "tipo", "titulo", "mensagem", "criadaEm");
        assertThat(item)
                .containsEntry("lidaEm", null)
                .containsEntry("id", notificacao.getId().toString());
        Map<String, Object> schemaDaContagem =
                documento.getMap("components.schemas.NotificacoesNaoLidasResponse.properties");
        assertThat(contagem.keySet()).containsExactlyInAnyOrderElementsOf(schemaDaContagem.keySet());

        String central = "paths.'/api/v1/notificacoes'.get.responses";
        String leitura = "paths.'/api/v1/notificacoes/{id}/leitura'.post.responses";
        String contador = "paths.'/api/v1/notificacoes/nao-lidas/contagem'.get.responses";
        assertThat(documento.getMap(central).keySet()).containsExactlyInAnyOrder("200", "400", "401");
        assertThat(documento.getMap(leitura).keySet()).containsExactlyInAnyOrder("200", "400", "401", "404");
        assertThat(documento.getMap(contador).keySet()).containsExactlyInAnyOrder("200", "401");
        assertThat(documento.getList("components.schemas.ErrorResponseDto.properties.codigo.enum", String.class))
                .contains("NTF-400-001", "NTF-404-001");
    }

    @Test
    void semToken_401NosTresEndpoints() {
        RestAssured.given().get("/api/v1/notificacoes").then().statusCode(401);
        RestAssured.given()
                .get("/api/v1/notificacoes/nao-lidas/contagem")
                .then()
                .statusCode(401);
        RestAssured.given()
                .post("/api/v1/notificacoes/{id}/leitura", UUID.randomUUID())
                .then()
                .statusCode(401);
    }
}
