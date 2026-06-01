package com.dynamis.sep_api.pix.application.service;

import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.StatusTransferenciaPixProvider;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaConcluidaEvent;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaFalhouEvent;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaSolicitadaEvent;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SincronizadorStatusTransferenciaTest {

    private ApplicationEventPublisher publisher;
    private SincronizadorStatusTransferencia sincronizador;

    @BeforeEach
    void setUp() {
        publisher = mock(ApplicationEventPublisher.class);
        sincronizador = new SincronizadorStatusTransferencia(publisher);
    }

    private PixTransferencia criada() {
        return PixTransferencia.criarDesembolso(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10000.00"),
                "a".repeat(64),
                "us****om",
                "idem-1",
                "corr-1");
    }

    private RespostaTransferenciaPix resp(StatusTransferenciaPixProvider status) {
        return new RespostaTransferenciaPix("ext-1", status);
    }

    @Test
    void solicitacaoPendente_ficaSolicitadaEPublicaSolicitada() {
        PixTransferencia t = criada();

        sincronizador.aplicarRespostaSolicitacao(t, resp(StatusTransferenciaPixProvider.PENDENTE));

        assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.SOLICITADA);
        assertThat(t.getExternalId()).isEqualTo("ext-1");
        verify(publisher).publishEvent(any(PixTransferenciaSolicitadaEvent.class));
    }

    @Test
    void solicitacaoConcluida_ficaConcluidaEPublicaSolicitadaEConcluida() {
        PixTransferencia t = criada();

        sincronizador.aplicarRespostaSolicitacao(t, resp(StatusTransferenciaPixProvider.CONCLUIDA));

        assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.CONCLUIDA);
        verify(publisher).publishEvent(any(PixTransferenciaSolicitadaEvent.class));
        verify(publisher).publishEvent(any(PixTransferenciaConcluidaEvent.class));
    }

    @Test
    void solicitacaoRejeitada_ficaFalhouEPublicaFalhou() {
        PixTransferencia t = criada();

        sincronizador.aplicarRespostaSolicitacao(t, resp(StatusTransferenciaPixProvider.REJEITADA));

        assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.FALHOU);
        verify(publisher).publishEvent(any(PixTransferenciaFalhouEvent.class));
        verify(publisher, never()).publishEvent(any(PixTransferenciaSolicitadaEvent.class));
    }

    @Test
    void solicitacaoSemExternalId_ficaFalhou() {
        PixTransferencia t = criada();

        sincronizador.aplicarRespostaSolicitacao(
                t, new RespostaTransferenciaPix("  ", StatusTransferenciaPixProvider.PENDENTE));

        assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.FALHOU);
    }

    @Test
    void sincronizar_solicitadaParaConcluida_avancaEPublica() {
        PixTransferencia t = criada();
        t.marcarSolicitada("ext-1");

        sincronizador.sincronizar(t, StatusTransferenciaPixProvider.CONCLUIDA);

        assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.CONCLUIDA);
        verify(publisher).publishEvent(any(PixTransferenciaConcluidaEvent.class));
    }

    @Test
    void sincronizar_statusTerminal_naoAltera() {
        PixTransferencia t = criada();
        t.marcarSolicitada("ext-1");
        t.marcarConcluida();

        sincronizador.sincronizar(t, StatusTransferenciaPixProvider.CONCLUIDA);

        assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.CONCLUIDA);
        // ja terminal: nenhum evento novo (so havia o publicado fora deste metodo, que nao ocorreu aqui)
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void sincronizar_pendente_naoAvanca() {
        PixTransferencia t = criada();
        t.marcarSolicitada("ext-1");

        sincronizador.sincronizar(t, StatusTransferenciaPixProvider.PENDENTE);

        assertThat(t.getStatus()).isEqualTo(StatusPixTransferencia.SOLICITADA);
    }
}
