package com.dynamis.sep_api.identity.infrastructure.security;

import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nenhuma variante de path alcanca o login (FMF-4.8), e portanto nenhuma burla o
 * {@link RateLimitFilter}.
 *
 * <p><b>Por que este teste existe.</b> O filtro casa o path com {@code equals} exato sobre
 * {@code request.getRequestURI()}, que e a URI <b>crua</b>, nao decodificada — enquanto o Spring MVC
 * decodifica e normaliza antes de rotear. A suspeita era que houvesse alguma forma que o dispatcher
 * roteia para o login e que o {@code equals} nao casa, entregando login sem limite a um cliente
 * anonimo. <b>Medido: nao ha.</b>
 *
 * <p><b>A razao e estrutural, e nao desenho explicito.</b> O {@code SecurityConfig:72} declara o
 * {@code permitAll} do login com o <b>mesmo literal</b> de path que o filtro. Variante que escapa do
 * {@code equals} do filtro escapa tambem do matcher de seguranca, cai em "autenticacao exigida" e
 * morre em {@code 401} antes do controller. As duas guardas falham na mesma direcao — por acidente de
 * alinhamento, nao por invariante escrita em lugar nenhum. <b>E e por isso que este teste precisa
 * existir</b>: afrouxar o matcher de seguranca (um pattern tolerante a barra final, por exemplo)
 * abriria o bypass sem que nada mais avisasse.
 *
 * <p><b>Por que a asserção e na tabela e nao no status.</b> O {@code 401} e a mesma resposta de "a
 * seguranca barrou antes do dispatcher" e de "o login rodou e o usuario nao existe" — afirmar sobre
 * ele nao distingue os dois casos, que sao justamente o que separa "seguro" de "bypass". A linha em
 * {@code login_attempt} so e gravada quando a requisicao chega ao caso de uso, entao <b>zero linhas e
 * a prova de que nao chegou</b>.
 *
 * <p><b>Nao esgota o limitador de proposito.</b> A primeira versao deste teste esgotava o orcamento
 * antes de cada variante para distinguir "contado" de "nao contado" pelo {@code 429}. Era
 * desnecessario — se a variante nao alcanca o login, nao ha o que limitar — e era instavel: o
 * limitador e por IP e compartilhado entre as invocacoes, entao a segunda ja comecava zerada. O
 * caminho canonico e a exaustao seguem cobertos pela {@code LockoutLoginIT} e pela
 * {@code RateLimitFilterTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VarianteDePathNaoBurlaRateLimitIT {

    private static final String CANONICO = "/api/v1/auth/login";

    /** Sufixo por execucao: o {@code sep_test} e reusado entre rodadas e a contagem tem de ser desta. */
    private static final String EXECUCAO = UUID.randomUUID().toString().substring(0, 8);

    /**
     * Mesmo par perfil + propriedade que {@code ReprocessoIT}, {@code CentralNotificacoesIT} e mais uma
     * duzia de ITs: a chave do cache de contexto e identica, entao esta classe <b>entra no contexto que
     * ja existe</b> em vez de subir um novo — o custo que o follow-up (aj38) cobra.
     *
     * <p>Serve para o teste nao disputar orcamento com quem divide o contexto padrao. As nove variantes
     * custam zero (path que nao casa sai do filtro antes do {@code computeIfAbsent}), mas o controle
     * canonico consome 1 dos 10 por minuto — pouco, e ainda assim acoplamento que nao precisa existir.
     */
    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.security.rate-limit.login-per-minute-per-ip", () -> 1000);
    }

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    private int postarLogin(String path, String username) {
        return RestAssured.given()
                .port(porta)
                .contentType("application/json")
                .body("{\"username\":\"" + username + "\",\"password\":\"senha-qualquer-longa\"}")
                .post(path)
                .statusCode();
    }

    private int tentativasGravadas(String username) {
        Integer linhas =
                jdbc.queryForObject("select count(*) from login_attempt where username = ?", Integer.class, username);
        return linhas == null ? 0 : linhas;
    }

    @ParameterizedTest(name = "{0} nao alcanca o login")
    @ValueSource(
            strings = {
                "/api/v1/auth/login/", // barra final
                "/api/v1//auth/login", // barra dupla no meio
                "//api/v1/auth/login", // barra dupla no inicio
                "/api/v1/auth/%6Cogin", // 'l' percent-encoded
                "/API/V1/AUTH/LOGIN", // caixa alta
                "/api/v1/./auth/login", // segmento ponto
                "/api/v1/outro/../auth/login", // segmento ponto-ponto
                "/api/v1/auth/login;x=1", // parametro de matriz
                "/api/v1/auth/login%20" // espaco codificado no fim
            })
    void varianteDePathNaoAlcancaOLogin(String variante) {
        String username = "fmf48-" + EXECUCAO + "-" + Integer.toHexString(variante.hashCode()) + "@sep.test";

        int status = postarLogin(variante, username);

        assertThat(tentativasGravadas(username))
                .as(
                        "gravar login_attempt significa que a variante chegou ao caso de uso por um path que o"
                                + " RateLimitFilter nao reconhece — bypass. Status observado: %s",
                        status)
                .isZero();
    }

    /**
     * <b>Controle do instrumento.</b> Fixa que o path canonico <b>alcanca</b> o login e grava a linha.
     * Sem isto, qualquer quebra da rota — ou da gravacao — faria as nove variantes marcarem zero e o
     * teste passaria provando nada.
     */
    @Test
    void oPathCanonicoAlcancaOLoginEGravaATentativa() {
        String username = "fmf48-canonico-" + EXECUCAO + "@sep.test";

        int status = postarLogin(CANONICO, username);

        assertThat(status).as("usuario inexistente: o login rodou e recusou").isEqualTo(401);
        assertThat(tentativasGravadas(username)).isEqualTo(1);
    }
}
