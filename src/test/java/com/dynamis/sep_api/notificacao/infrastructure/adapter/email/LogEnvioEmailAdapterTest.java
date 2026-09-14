package com.dynamis.sep_api.notificacao.infrastructure.adapter.email;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dynamis.sep_api.notificacao.application.port.out.dto.EmailNotificacao;
import com.dynamis.sep_api.notificacao.application.port.out.dto.ResultadoEnvioEmail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class LogEnvioEmailAdapterTest {

    private final LogEnvioEmailAdapter adapter = new LogEnvioEmailAdapter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LogEnvioEmailAdapter.class);
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
    void simula_eNuncaDeclaraEnvio() {
        ResultadoEnvioEmail resultado = adapter.enviar(new EmailNotificacao("cliente@sep.test", "Assunto", "Corpo"));

        assertThat(resultado).isEqualTo(ResultadoEnvioEmail.SIMULADO);
    }

    @Test
    void naoRegistraDestinatarioAssuntoOuCorpo() {
        adapter.enviar(new EmailNotificacao("cliente@sep.test", "Conta bloqueada", "CPF 12345678900; token secreto"));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent evento = appender.list.getFirst();
        assertThat(evento.getFormattedMessage() + evento.getKeyValuePairs())
                .doesNotContain("cliente@sep.test", "Conta bloqueada", "12345678900", "token secreto")
                .contains("Envio de email simulado", "notification_simulated");
    }
}
