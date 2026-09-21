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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ramos dos handlers de {@code 415} e {@code 406} (FMF-4.2). A {@code MediaTypeNaoSuportadoIT} prova
 * que estao ligados a cadeia real; aqui se prova a <b>logica</b>, incluindo os ramos sem lista de
 * tipos, que o dispatcher nao produz para um mapping existente.
 *
 * <p>Espelha a {@code MetodoNaoSuportadoTest}, que cobre o irmao {@code 405}.
 */
class MediaTypeNaoSuportadoTest {

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

    // ---------------------------------------------------------------- 415

    /** Mesma regra do {@code 405}: corpo e header anunciam a mesma lista, na mesma ordem. */
    @Test
    void mensagemEHeaderDo415UsamAMesmaOrdem() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON));

        ResponseEntity<ErrorResponseDto> resposta =
                handler.handleMediaTypeNotSupported(ex, request("/api/v1/auth/login"));

        assertThat(resposta.getStatusCode().value()).isEqualTo(415);
        assertThat(resposta.getBody().message())
                .as("ordem alfabetica, nao a de declaracao do controller")
                .isEqualTo("Content-Type text/plain nao suportado nesta rota. Tipos aceitos:"
                        + " application/json, application/xml");
        assertThat(resposta.getHeaders().get(HttpHeaders.ACCEPT))
                .as("corpo e header nao podem anunciar a mesma lista em ordens diferentes")
                .containsExactly("application/json, application/xml");
    }

    @Test
    void corpoDo415TrazPathStatusEError() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ErrorResponseDto> resposta = handler.handleMediaTypeNotSupported(ex, request("/api/v1/rota"));

        assertThat(resposta.getBody().status()).isEqualTo(415);
        assertThat(resposta.getBody().error()).isEqualTo("Unsupported Media Type");
        assertThat(resposta.getBody().path()).isEqualTo("/api/v1/rota");
    }

    /**
     * Ramo sem lista de tipos: o dispatcher nao o produz para um mapping existente, mas o construtor
     * publico permite, e sem este teste a mensagem sairia terminada em "Tipos aceitos: " vazio.
     */
    @Test
    void semTiposAceitosA415NaoAnunciaListaNemHeader() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of());

        ResponseEntity<ErrorResponseDto> resposta = handler.handleMediaTypeNotSupported(ex, request("/api/v1/rota"));

        assertThat(resposta.getBody().message()).isEqualTo("Content-Type text/plain nao suportado nesta rota");
        assertThat(resposta.getHeaders().get(HttpHeaders.ACCEPT)).isNull();
    }

    /** {@code Content-Type} ausente e caso real: {@code POST} sem header nenhum. */
    @Test
    void contentTypeAusenteNoBranco() {
        // O cast escolhe o construtor: `null` puro e ambiguo entre (MediaType, ...) e (String, ...).
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException((MediaType) null, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ErrorResponseDto> resposta = handler.handleMediaTypeNotSupported(ex, request("/api/v1/rota"));

        assertThat(resposta.getBody().message())
                .as("sem isso a mensagem diria 'Content-Type null'")
                .startsWith("Content-Type ausente nao suportado");
    }

    @Test
    void o415LogaEmWarnComOPath() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        handler.handleMediaTypeNotSupported(ex, request("/api/v1/auth/login"));

        assertThat(appender.list)
                .as("erro de cliente nao merece ERROR, mas rajada e sinal de integracao quebrada")
                .singleElement()
                .satisfies(evento -> {
                    assertThat(evento.getLevel()).isEqualTo(Level.WARN);
                    assertThat(evento.getKeyValuePairs())
                            .anySatisfy(kv -> assertThat(kv.key).isEqualTo("event"));
                });
    }

    // ---------------------------------------------------------------- 406

    /**
     * <b>Corpo vazio e o contrato, nao um detalhe.</b> Medido na FMF-4.2: devolver
     * {@code ErrorResponseDto} aqui reabre a negociacao que causou a excecao, a escrita falha, e o
     * cliente recebe <b>401</b> do entry point de seguranca em vez do {@code 406}.
     */
    @Test
    void o406NaoTemCorpo() {
        HttpMediaTypeNotAcceptableException ex =
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<Void> resposta = handler.handleMediaTypeNotAcceptable(ex, request("/v3/api-docs"));

        assertThat(resposta.getStatusCode().value()).isEqualTo(406);
        assertThat(resposta.getBody()).isNull();
    }

    @Test
    void o406AnunciaOsTiposDisponiveisEmOrdem() {
        HttpMediaTypeNotAcceptableException ex =
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON));

        ResponseEntity<Void> resposta = handler.handleMediaTypeNotAcceptable(ex, request("/v3/api-docs"));

        assertThat(resposta.getHeaders().get(HttpHeaders.ACCEPT)).containsExactly("application/json, application/xml");
    }

    @Test
    void semTiposDisponiveisO406NaoAnunciaHeader() {
        HttpMediaTypeNotAcceptableException ex = new HttpMediaTypeNotAcceptableException(List.of());

        ResponseEntity<Void> resposta = handler.handleMediaTypeNotAcceptable(ex, request("/v3/api-docs"));

        assertThat(resposta.getStatusCode().value()).isEqualTo(406);
        assertThat(resposta.getHeaders().get(HttpHeaders.ACCEPT)).isNull();
    }

    @Test
    void o406LogaEmWarn() {
        HttpMediaTypeNotAcceptableException ex =
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));
        MockHttpServletRequest request = request("/v3/api-docs");
        request.addHeader(HttpHeaders.ACCEPT, "application/xml");

        handler.handleMediaTypeNotAcceptable(ex, request);

        assertThat(appender.list).singleElement().satisfies(evento -> assertThat(evento.getLevel())
                .isEqualTo(Level.WARN));
    }
}
