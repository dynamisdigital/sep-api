package com.dynamis.sep_api.shared.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ramos do handler de {@code 405} (Sprint 35 Task 35.3, hotfix do review). A
 * {@code MetodoNaoSuportadoIT} prova que o handler esta ligado a cadeia real; aqui se prova a
 * <b>logica</b>, incluindo os dois ramos que nenhuma rota do projeto alcanca hoje.
 *
 * <p>Chamar o handler direto e proposital: o ramo sem metodos suportados exige um
 * {@code HttpRequestMethodNotSupportedException} construido sem lista, que o dispatcher nunca produz
 * para um mapping existente. Um teste por HTTP nao consegue chegar la.
 */
class MetodoNaoSuportadoTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level nivelAnterior;

    @BeforeEach
    void configurarAppender() {
        nivelAnterior = logger.getLevel();
        logger.setLevel(Level.WARN);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void removerAppender() {
        logger.detachAppender(appender);
        logger.setLevel(nivelAnterior);
        appender.stop();
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }

    @Test
    void mensagemEHeaderUsamAMesmaOrdem() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("PUT", List.of("POST", "GET", "DELETE"));

        ResponseEntity<ErrorResponseDto> resposta = handler.handleMethodNotSupported(ex, request("/api/v1/pix/chaves"));

        assertThat(resposta.getBody().message())
                .as("ordem alfabetica, nao a de declaracao do controller")
                .isEqualTo("Metodo PUT nao suportado nesta rota. Metodos aceitos: DELETE, GET, POST");
        assertThat(resposta.getHeaders().get(HttpHeaders.ALLOW))
                .as("corpo e header nao podem anunciar a mesma lista em ordens diferentes")
                .containsExactly("DELETE,GET,POST");
    }

    @Test
    void corpoTrazPathEStatus() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PUT", List.of("GET"));

        ResponseEntity<ErrorResponseDto> resposta = handler.handleMethodNotSupported(ex, request("/api/v1/rota"));

        assertThat(resposta.getStatusCode().value()).isEqualTo(405);
        assertThat(resposta.getBody().status()).isEqualTo(405);
        assertThat(resposta.getBody().error()).isEqualTo("Method Not Allowed");
        assertThat(resposta.getBody().path()).isEqualTo("/api/v1/rota");
    }

    /**
     * {@code getSupportedHttpMethods()} devolve {@code null} quando a excecao nasce sem lista. Emitir
     * {@code Allow} vazio seria pior que omitir: pela RFC 9110 §10.2.1 um {@code Allow} vazio afirma
     * que o recurso <b>nao aceita metodo nenhum</b>, o oposto da verdade.
     */
    @Test
    void semMetodosConhecidos_naoEmiteAllowEMensagemNaoPrometeLista() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PUT");

        ResponseEntity<ErrorResponseDto> resposta = handler.handleMethodNotSupported(ex, request("/api/v1/rota"));

        assertThat(resposta.getStatusCode().value()).isEqualTo(405);
        assertThat(resposta.getHeaders().containsKey(HttpHeaders.ALLOW)).isFalse();
        assertThat(resposta.getBody().message())
                .isEqualTo("Metodo PUT nao suportado nesta rota")
                .doesNotContain("aceitos");
    }

    @Test
    void listaVazia_tratadaComoDesconhecida() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PUT", List.of());

        ResponseEntity<ErrorResponseDto> resposta = handler.handleMethodNotSupported(ex, request("/api/v1/rota"));

        assertThat(resposta.getHeaders().containsKey(HttpHeaders.ALLOW)).isFalse();
        assertThat(resposta.getBody().message()).isEqualTo("Metodo PUT nao suportado nesta rota");
    }

    @Test
    void metodoDaRequestEhEcoadoNaMensagem() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH", List.of("GET"));

        ResponseEntity<ErrorResponseDto> resposta = handler.handleMethodNotSupported(ex, request("/api/v1/rota"));

        assertThat(resposta.getBody().message()).startsWith("Metodo PATCH ");
    }

    /**
     * Antes da Task 35.3 o caso passava pelo {@code handleGeneric} e deixava rastro em {@code ERROR}.
     * Corrigir o status sem repor observabilidade trocaria um defeito por outro: uma rajada de
     * {@code 405} e o sinal de integracao quebrada, e sem log ela fica invisivel.
     */
    @Test
    void registraEmWarnComOMetodoEOPath() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PUT", List.of("GET"));

        handler.handleMethodNotSupported(ex, request("/api/v1/pix/chaves"));

        assertThat(appender.list).singleElement().satisfies(evento -> {
            assertThat(evento.getLevel())
                    .as("erro de cliente nao merece ERROR, mas precisa ser diagnosticavel")
                    .isEqualTo(Level.WARN);
            Map<String, String> campos = evento.getKeyValuePairs().stream()
                    .collect(Collectors.toMap(par -> par.key, par -> String.valueOf(par.value)));
            assertThat(campos)
                    .containsEntry("event", "method_not_allowed")
                    .containsEntry("metodo", "PUT")
                    .containsEntry("path", "/api/v1/pix/chaves");
        });
    }
}
