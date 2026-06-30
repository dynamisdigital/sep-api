package com.dynamis.sep_api.shared.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level nivelAnterior;

    @BeforeEach
    void configurarAppender() {
        nivelAnterior = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void removerAppender() {
        logger.detachAppender(appender);
        logger.setLevel(nivelAnterior);
        appender.stop();
    }

    @Test
    void registraCamposEstruturadosSemQueryString() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/propostas");
        request.setQueryString("token=segredo&cpf=12345678900");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> response.setStatus(201));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent evento = appender.list.getFirst();
        Map<String, Object> campos =
                evento.getKeyValuePairs().stream().collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(campos)
                .containsEntry("event", "http_request_completed")
                .containsEntry("method", "POST")
                .containsEntry("path", "/api/v1/propostas")
                .containsEntry("status", 201)
                .containsKey("durationMs");
        assertThat(evento.getFormattedMessage()).doesNotContain("token", "segredo", "cpf", "12345678900");
    }

    @Test
    void naoRegistraEndpointsDoActuator() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        assertThat(appender.list).isEmpty();
    }
}
