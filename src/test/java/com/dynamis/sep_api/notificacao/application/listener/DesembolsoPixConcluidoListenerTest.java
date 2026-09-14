package com.dynamis.sep_api.notificacao.application.listener;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dynamis.sep_api.notificacao.application.usecase.NotificarUsuarioUseCase;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.Referencia;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.TipoReferencia;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaConcluidaEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DesembolsoPixConcluidoListenerTest {

    private static final UUID TRANSFERENCIA = UUID.randomUUID();
    private static final UUID CONTRATO = UUID.randomUUID();
    private static final UUID TOMADOR = UUID.randomUUID();
    private static final String EXTERNAL_ID = "e2e-provider-E123456789";

    private final NotificarUsuarioUseCase notificar = mock(NotificarUsuarioUseCase.class);
    private final DesembolsoPixConcluidoListener listener = new DesembolsoPixConcluidoListener(notificar);

    private final Logger logger = (Logger) LoggerFactory.getLogger(DesembolsoPixConcluidoListener.class);
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

    @Test
    void disponibilizaNaCentralDoTomadorComATransferenciaComoOrigemEOContratoComoReferencia() {
        listener.aoConcluir(new PixTransferenciaConcluidaEvent(TRANSFERENCIA, CONTRATO, TOMADOR, EXTERNAL_ID));

        verify(notificar)
                .disponibilizarNaCentral(
                        TOMADOR,
                        new OrigemNotificacao(TipoNotificacao.DESEMBOLSO_PIX_CONCLUIDO, TRANSFERENCIA.toString()),
                        new ConteudoNotificacao(
                                "Desembolso concluido",
                                "A transferencia Pix do desembolso do seu contrato foi concluida.",
                                new Referencia(TipoReferencia.CONTRATO, CONTRATO)));
    }

    @Test
    void naoUsaOIdentificadorDoProvider() {
        ConteudoNotificacao conteudo = DesembolsoPixConcluidoListener.conteudo(
                new PixTransferenciaConcluidaEvent(TRANSFERENCIA, CONTRATO, TOMADOR, EXTERNAL_ID));
        OrigemNotificacao origem = DesembolsoPixConcluidoListener.origem(
                new PixTransferenciaConcluidaEvent(TRANSFERENCIA, CONTRATO, TOMADOR, EXTERNAL_ID));

        assertThat(conteudo.toString() + origem).doesNotContain(EXTERNAL_ID);
    }

    @Test
    void semTomador_naoHaDestinatario() {
        listener.aoConcluir(new PixTransferenciaConcluidaEvent(TRANSFERENCIA, CONTRATO, null, EXTERNAL_ID));

        verifyNoInteractions(notificar);
    }

    @Test
    void semContrato_notificaSemReferencia() {
        listener.aoConcluir(new PixTransferenciaConcluidaEvent(TRANSFERENCIA, null, TOMADOR, EXTERNAL_ID));

        verify(notificar)
                .disponibilizarNaCentral(
                        TOMADOR,
                        new OrigemNotificacao(TipoNotificacao.DESEMBOLSO_PIX_CONCLUIDO, TRANSFERENCIA.toString()),
                        new ConteudoNotificacao(
                                DesembolsoPixConcluidoListener.TITULO, DesembolsoPixConcluidoListener.MENSAGEM, null));
    }

    @Test
    void falhaAoNotificar_naoSobeELogaSemIdentificadores() {
        doThrow(new IllegalStateException("insert falhou para " + TOMADOR + " " + EXTERNAL_ID))
                .when(notificar)
                .disponibilizarNaCentral(any(), any(), any());

        assertThatNoException()
                .isThrownBy(() -> listener.aoConcluir(
                        new PixTransferenciaConcluidaEvent(TRANSFERENCIA, CONTRATO, TOMADOR, EXTERNAL_ID)));

        assertThat(appender.list).singleElement().satisfies(log -> {
            assertThat(log.getFormattedMessage() + log.getKeyValuePairs())
                    .contains("notification_not_recorded", "java.lang.IllegalStateException")
                    .doesNotContain(TOMADOR.toString(), EXTERNAL_ID, "insert falhou");
            assertThat(log.getThrowableProxy()).isNull();
        });
    }
}
