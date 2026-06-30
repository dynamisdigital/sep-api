package com.dynamis.sep_api.shared.email;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class LogEmailServiceTest {

    private final LogEmailService service = new LogEmailService();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LogEmailService.class);
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
    void naoRegistraDestinatarioAssuntoOuCorpo() {
        service.enviar("cliente@sep.test", "Conta bloqueada", "CPF 12345678900; token secreto");

        assertThat(appender.list).hasSize(1);
        String mensagem = appender.list.getFirst().getFormattedMessage();
        assertThat(mensagem)
                .doesNotContain("cliente@sep.test", "Conta bloqueada", "12345678900", "token secreto")
                .contains("Envio de email simulado");
    }
}
