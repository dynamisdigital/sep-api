package com.dynamis.sep_api.shared.exception;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Metodo HTTP nao suportado devolve {@code 405} com o corpo de erro padronizado (Sprint 35
 * Task 35.3). Ate aqui a excecao caia no handler generico e o cliente recebia {@code 500}.
 *
 * <p><b>Por que {@code /v3/api-docs} e nao uma rota de negocio.</b> Medido no
 * {@code SecurityConfig}: todos os {@code permitAll} de {@code /api/v1/**} fixam o metodo, entao um
 * verbo errado ali e barrado com {@code 401} pelo {@code apiAuthenticationEntryPoint} <b>antes</b>
 * do dispatcher — o {@code 405} nunca acontece. Os matchers de {@code :62-68} nao fixam metodo, e
 * por isso sao o unico caminho em que o {@code 405} e observavel sem montar sessao. A rota so
 * fornece o gatilho; o que se afirma aqui e o handler, que e global.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MetodoNaoSuportadoIT {

    private static final String ROTA_SOMENTE_GET = "/v3/api-docs";

    @LocalServerPort
    private int porta;

    @Test
    void verboNaoSuportadoDevolve405ComCorpoPadronizado() {
        Response resposta = RestAssured.given().port(porta).post(ROTA_SOMENTE_GET);

        assertThat(resposta.statusCode())
                .as("erro do cliente; antes da Task 35.3 o fallback generico anunciava 500")
                .isEqualTo(405);
        assertThat(resposta.jsonPath().getInt("status")).isEqualTo(405);
        assertThat(resposta.jsonPath().getString("error")).isEqualTo("Method Not Allowed");
        assertThat(resposta.jsonPath().getString("path")).isEqualTo(ROTA_SOMENTE_GET);
        assertThat(resposta.jsonPath().getString("message"))
                .as("a mensagem diz o que fazer, nao 'consulte o suporte'; a string inteira e contrato"
                        + " de produto, entao nao se assere por pedaco")
                .isEqualTo("Metodo POST nao suportado nesta rota. Metodos aceitos: GET");
        assertThat(resposta.jsonPath().getString("traceId")).isNotBlank();
    }

    /** RFC 9110 §15.5.6 exige {@code Allow} no {@code 405}. */
    @Test
    void resposta405TrazOHeaderAllow() {
        Response resposta = RestAssured.given().port(porta).post(ROTA_SOMENTE_GET);

        assertThat(resposta.header("Allow")).isNotNull().contains("GET");
    }

    /**
     * Fixa a premissa do gatilho. Se a rota sumir — {@code springdoc.api-docs.enabled: false} ou path
     * trocado —, os dois testes acima falham por {@code 404}, e o primeiro falha sob um {@code as()}
     * que culpa o fallback generico. Esta e a unica falha que nomeia a causa real.
     */
    @Test
    void oMesmoCaminhoComOVerboCertoSegueRespondendo() {
        Response resposta = RestAssured.given().port(porta).get(ROTA_SOMENTE_GET);

        assertThat(resposta.statusCode()).isEqualTo(200);
    }
}
