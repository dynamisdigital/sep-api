package com.dynamis.sep_api.cobranca.application.listener;

import com.dynamis.sep_api.cobranca.application.dto.GerarAgendaPagamentoCommand;
import com.dynamis.sep_api.cobranca.application.port.out.PropostaCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.port.out.PropostaCobrancaView;
import com.dynamis.sep_api.cobranca.application.service.calculo.ParametrosCobrancaProperties;
import com.dynamis.sep_api.cobranca.application.service.calculo.SistemaAmortizacao;
import com.dynamis.sep_api.cobranca.application.usecase.GerarAgendaPagamentoUseCase;
import com.dynamis.sep_api.contratos.domain.event.ContratoAssinadoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContratoAssinadoListenerTest {

    private GerarAgendaPagamentoUseCase useCase;
    private PropostaCobrancaQueryPort propostaQueryPort;
    private ParametrosCobrancaProperties properties;
    private ContratoAssinadoListener listener;

    @BeforeEach
    void setup() {
        useCase = mock(GerarAgendaPagamentoUseCase.class);
        propostaQueryPort = mock(PropostaCobrancaQueryPort.class);
        properties = new ParametrosCobrancaProperties();
        listener = new ContratoAssinadoListener(useCase, propostaQueryPort, properties);
    }

    @Test
    void usaTaxaEstruturadaQuandoPropostaPersistirTaxa() {
        UUID contratoId = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        BigDecimal taxaAprovada = new BigDecimal("0.025");
        OffsetDateTime dataAssinatura = OffsetDateTime.parse("2026-05-01T10:00:00-03:00");
        when(propostaQueryPort.buscarPorId(propostaId))
                .thenReturn(Optional.of(
                        new PropostaCobrancaView(propostaId, new BigDecimal("10000"), 12, Optional.of(taxaAprovada))));

        listener.aoAssinar(novoEvento(contratoId, propostaId, tomadorId, dataAssinatura));

        ArgumentCaptor<GerarAgendaPagamentoCommand> captor = ArgumentCaptor.forClass(GerarAgendaPagamentoCommand.class);
        verify(useCase).executar(captor.capture());
        GerarAgendaPagamentoCommand cmd = captor.getValue();
        assertThat(cmd.contratoId()).isEqualTo(contratoId);
        assertThat(cmd.propostaId()).isEqualTo(propostaId);
        assertThat(cmd.tomadorId()).isEqualTo(tomadorId);
        assertThat(cmd.valorFinanciado()).isEqualByComparingTo("10000");
        assertThat(cmd.numeroParcelas()).isEqualTo(12);
        assertThat(cmd.taxaMensal()).isEqualByComparingTo(taxaAprovada);
        assertThat(cmd.dataBase()).isEqualTo(dataAssinatura.toLocalDate());
        assertThat(cmd.sistema()).isEqualTo(SistemaAmortizacao.PRICE);
    }

    @Test
    void usaTaxaDefaultDeConfigQuandoPropostaSemTaxa() {
        UUID propostaId = UUID.randomUUID();
        when(propostaQueryPort.buscarPorId(propostaId))
                .thenReturn(Optional.of(
                        new PropostaCobrancaView(propostaId, new BigDecimal("10000"), 12, Optional.empty())));

        listener.aoAssinar(novoEvento(UUID.randomUUID(), propostaId, UUID.randomUUID(), OffsetDateTime.now()));

        ArgumentCaptor<GerarAgendaPagamentoCommand> captor = ArgumentCaptor.forClass(GerarAgendaPagamentoCommand.class);
        verify(useCase).executar(captor.capture());
        assertThat(captor.getValue().taxaMensal()).isEqualByComparingTo(properties.getTaxaJurosMensalDefault());
    }

    @Test
    void propostaInexistente_loga_nao_relanca() {
        UUID propostaId = UUID.randomUUID();
        when(propostaQueryPort.buscarPorId(propostaId)).thenReturn(Optional.empty());

        listener.aoAssinar(novoEvento(UUID.randomUUID(), propostaId, UUID.randomUUID(), OffsetDateTime.now()));

        verify(useCase, never()).executar(any());
    }

    @Test
    void useCaseLancaExcecao_loga_nao_relanca() {
        UUID propostaId = UUID.randomUUID();
        when(propostaQueryPort.buscarPorId(propostaId))
                .thenReturn(Optional.of(new PropostaCobrancaView(
                        propostaId, new BigDecimal("10000"), 12, Optional.of(new BigDecimal("0.02")))));
        when(useCase.executar(any())).thenThrow(new RuntimeException("falha simulada"));

        listener.aoAssinar(novoEvento(UUID.randomUUID(), propostaId, UUID.randomUUID(), OffsetDateTime.now()));
        // Comportamento esperado: log warn + sem propagar (REQUIRES_NEW).
    }

    private static ContratoAssinadoEvent novoEvento(
            UUID contratoId, UUID propostaId, UUID tomadorId, OffsetDateTime dataAssinatura) {
        return new ContratoAssinadoEvent(
                contratoId,
                propostaId,
                tomadorId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "clicksign",
                "env-x",
                "abcdef",
                dataAssinatura);
    }
}
