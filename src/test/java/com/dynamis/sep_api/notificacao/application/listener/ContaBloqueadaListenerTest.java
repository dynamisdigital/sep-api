package com.dynamis.sep_api.notificacao.application.listener;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dynamis.sep_api.identity.domain.event.ContaBloqueadaEvent;
import com.dynamis.sep_api.notificacao.application.usecase.NotificarUsuarioUseCase;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ContaBloqueadaListenerTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final OffsetDateTime BLOQUEADA_EM =
            OffsetDateTime.of(2026, 9, 14, 10, 5, 7, 123_456_000, ZoneOffset.ofHours(-3));

    private final NotificarUsuarioUseCase notificar = mock(NotificarUsuarioUseCase.class);
    private final ContaBloqueadaListener listener = new ContaBloqueadaListener(notificar);

    private final Logger logger = (Logger) LoggerFactory.getLogger(ContaBloqueadaListener.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void capturarLog() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void soltarLog() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private static ContaBloqueadaEvent evento() {
        return new ContaBloqueadaEvent(USUARIO, "cliente@sep.test", BLOQUEADA_EM, 30);
    }

    @Test
    void enviaOMesmoEmailQueOLockoutServiceEnviava() {
        listener.aoBloquear(evento());

        verify(notificar)
                .enviarEmail(
                        USUARIO,
                        "cliente@sep.test",
                        new OrigemNotificacao(TipoNotificacao.CONTA_BLOQUEADA, "2026-09-14T13:05:07.123456Z"),
                        new ConteudoNotificacao(
                                "Conta SEP bloqueada temporariamente",
                                "Detectamos varias tentativas de login. Sua conta esta bloqueada por 30 minutos.",
                                null));
    }

    @Test
    void mesmoBloqueioEmOutroFuso_temAMesmaOrigem() {
        ContaBloqueadaEvent emUtc = new ContaBloqueadaEvent(
                USUARIO, "cliente@sep.test", BLOQUEADA_EM.withOffsetSameInstant(ZoneOffset.UTC), 30);

        assertThat(ContaBloqueadaListener.origem(emUtc)).isEqualTo(ContaBloqueadaListener.origem(evento()));
    }

    @Test
    void falhaAoNotificar_naoSobeELogaSemEnderecoNemMensagem() {
        doThrow(new IllegalStateException("banco recusou cliente@sep.test"))
                .when(notificar)
                .enviarEmail(any(), any(), any(), any());

        assertThatNoException().isThrownBy(() -> listener.aoBloquear(evento()));

        assertThat(appender.list).singleElement().satisfies(log -> {
            assertThat(log.getFormattedMessage() + log.getKeyValuePairs())
                    .contains("notification_not_recorded", "java.lang.IllegalStateException")
                    .doesNotContain("cliente@sep.test", "banco recusou");
            assertThat(log.getThrowableProxy()).isNull();
        });
    }
}
