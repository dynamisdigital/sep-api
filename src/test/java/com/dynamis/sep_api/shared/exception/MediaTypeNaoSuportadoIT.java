package com.dynamis.sep_api.shared.exception;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code Content-Type} e {@code Accept} nao suportados devolvem {@code 415} e {@code 406} com o corpo
 * de erro padronizado (FMF-4.2). Ate aqui os dois caiam no {@code handleGeneric} e o cliente recebia
 * {@code 500} com {@code ERROR unhandled_exception} no log — e o {@code 415} e alcancavel <b>sem
 * autenticacao</b>, o que dava a um cliente anonimo o poder de encher o log de erro.
 *
 * <p>Irmaos do {@code 405} que a Sprint 35 Task 35.3 fechou: a {@code MetodoNaoSuportadoIT} e o
 * modelo deste arquivo, e o follow-up (a) daquela sprint nomeava os tres.
 *
 * <p><b>Gatilhos.</b> O {@code 415} usa {@code POST /api/v1/auth/login}, que e {@code permitAll} com
 * metodo fixo — o verbo esta certo, entao a requisicao chega ao dispatcher e morre na negociacao de
 * {@code Content-Type}. O {@code 406} usa {@code GET /v3/api-docs} pela mesma razao da
 * {@code MetodoNaoSuportadoIT}: e um {@code permitAll} sem sessao a montar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MediaTypeNaoSuportadoIT {

    private static final String ROTA_LOGIN = "/api/v1/auth/login";
    private static final String ROTA_JSON = "/v3/api-docs";

    @LocalServerPort
    private int porta;

    @Test
    void contentTypeNaoSuportadoDevolve415ComCorpoPadronizado() {
        Response resposta = RestAssured.given()
                .port(porta)
                .contentType("text/plain")
                .body("nao e json")
                .post(ROTA_LOGIN);

        assertThat(resposta.statusCode())
                .as("erro do cliente; antes da FMF-4.2 o fallback generico anunciava 500")
                .isEqualTo(415);
        assertThat(resposta.jsonPath().getInt("status")).isEqualTo(415);
        assertThat(resposta.jsonPath().getString("error")).isEqualTo("Unsupported Media Type");
        assertThat(resposta.jsonPath().getString("path")).isEqualTo(ROTA_LOGIN);
        assertThat(resposta.jsonPath().getString("traceId")).isNotBlank();
    }

    /** RFC 9110 §15.5.16: o {@code 415} anuncia o que aceitaria. */
    @Test
    void resposta415AnunciaOsTiposAceitos() {
        Response resposta = RestAssured.given()
                .port(porta)
                .contentType("text/plain")
                .body("nao e json")
                .post(ROTA_LOGIN);

        assertThat(resposta.header("Accept")).isNotNull().contains("application/json");
    }

    /**
     * <b>O sintoma do 406 nao era 500, e isso foi medido.</b> O follow-up (a) da Sprint 35 registrava
     * "415/406 caem em 500"; a sonda da FMF-4.2 encontrou <b>401</b> nas tres rotas publicas testadas.
     * A causa: o {@code handleGeneric} devolvia corpo JSON, o cliente acabara de dizer que nao aceita
     * JSON, a escrita falhava na mesma negociacao e a falha subia ate o
     * {@code ApiAuthenticationEntryPoint}, que respondia 401 ignorando o {@code Accept}.
     *
     * <p>Por isso o handler do 406 devolve {@code ResponseEntity<Void>}. Este teste e o que trava
     * essa decisao: voltar a devolver corpo aqui faz o status cair para 401 de novo.
     */
    @Test
    void acceptNaoSatisfeitoDevolve406SemCorpo() {
        Response resposta =
                RestAssured.given().port(porta).accept("application/xml").get(ROTA_JSON);

        assertThat(resposta.statusCode())
                .as("antes da FMF-4.2 vinha 401, nao 500 — corpo JSON irrespondivel virava falha de"
                        + " autenticacao na cadeia de filtros")
                .isEqualTo(406);
        assertThat(resposta.body().asString())
                .as("corpo vazio e o que faz o 406 chegar; qualquer corpo reabre a negociacao que falhou")
                .isEmpty();
    }

    /** Os tipos disponiveis vao no header, que nao passa por negociacao de conteudo. */
    @Test
    void resposta406AnunciaOsTiposDisponiveis() {
        Response resposta =
                RestAssured.given().port(porta).accept("application/xml").get(ROTA_JSON);

        assertThat(resposta.header("Accept")).isNotNull().contains("application/json");
    }

    /**
     * Rota autenticada continua parando na seguranca, antes do dispatcher — o 406 nem chega a existir
     * ali. Fixa o alcance real do handler, pela mesma razao que a {@code MetodoNaoSuportadoIT}
     * documenta para o 405.
     */
    @Test
    void rotaAutenticadaSegueRespondendo401EnaoO406() {
        Response resposta =
                RestAssured.given().port(porta).accept("application/xml").get("/api/v1/notificacoes");

        assertThat(resposta.statusCode()).isEqualTo(401);
    }

    /**
     * Fixa a premissa dos gatilhos: se as rotas sumirem, os testes acima falham por {@code 404} e nao
     * por regressao do handler. Mesma protecao da {@code MetodoNaoSuportadoIT}.
     */
    @Test
    void asRotasUsadasComoGatilhoSeguemDePe() {
        assertThat(RestAssured.given().port(porta).get(ROTA_JSON).statusCode()).isEqualTo(200);
        assertThat(RestAssured.given()
                        .port(porta)
                        .contentType("application/json")
                        .body("{}")
                        .post(ROTA_LOGIN)
                        .statusCode())
                .as("com JSON a rota responde erro de validacao, nao 415")
                .isNotEqualTo(415);
    }
}
