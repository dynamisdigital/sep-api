package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.GerarAgendaPagamentoCommand;
import com.dynamis.sep_api.cobranca.application.port.out.AgendaPagamentoCobrancaPort;
import com.dynamis.sep_api.cobranca.application.service.calculo.AmortizacaoDispatcher;
import com.dynamis.sep_api.cobranca.application.service.calculo.CalculadoraPrice;
import com.dynamis.sep_api.cobranca.application.service.calculo.CalculadoraSAC;
import com.dynamis.sep_api.cobranca.application.service.calculo.ParametrosCobrancaProperties;
import com.dynamis.sep_api.cobranca.application.service.calculo.SistemaAmortizacao;
import com.dynamis.sep_api.cobranca.domain.event.AgendaGeradaEvent;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GerarAgendaPagamentoUseCaseTest {

    private AgendaPagamentoCobrancaPort agendaPort;
    private AmortizacaoDispatcher dispatcher;
    private ParametrosCobrancaProperties properties;
    private ApplicationEventPublisher eventPublisher;
    private PlatformTransactionManager txManager;
    private GerarAgendaPagamentoUseCase useCase;

    @BeforeEach
    void setup() {
        agendaPort = mock(AgendaPagamentoCobrancaPort.class);
        dispatcher = new AmortizacaoDispatcher(List.of(new CalculadoraPrice(), new CalculadoraSAC()));
        properties = new ParametrosCobrancaProperties();
        eventPublisher = mock(ApplicationEventPublisher.class);
        txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        useCase = new GerarAgendaPagamentoUseCase(agendaPort, dispatcher, properties, eventPublisher, txManager);
    }

    @Test
    void gera_agenda_e_publica_evento_quando_inexistente() {
        UUID contratoId = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        when(agendaPort.buscarAtivaPorContrato(contratoId)).thenReturn(Optional.empty());
        when(agendaPort.salvarEFlush(any(AgendaPagamento.class))).thenAnswer(inv -> inv.getArgument(0));

        AgendaPagamento agenda = useCase.executar(new GerarAgendaPagamentoCommand(
                contratoId,
                propostaId,
                tomadorId,
                new BigDecimal("12000"),
                12,
                new BigDecimal("0.02"),
                LocalDate.of(2026, 5, 1),
                SistemaAmortizacao.PRICE));

        assertThat(agenda.getContratoId()).isEqualTo(contratoId);
        assertThat(agenda.getNumeroParcelas()).isEqualTo(12);
        assertThat(agenda.getParcelas()).hasSize(12);

        ArgumentCaptor<AgendaGeradaEvent> captor = ArgumentCaptor.forClass(AgendaGeradaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AgendaGeradaEvent ev = captor.getValue();
        assertThat(ev.contratoId()).isEqualTo(contratoId);
        assertThat(ev.propostaId()).isEqualTo(propostaId);
        assertThat(ev.tomadorId()).isEqualTo(tomadorId);
        assertThat(ev.numeroParcelas()).isEqualTo(12);
    }

    @Test
    void gera_agenda_24_parcelas() {
        UUID contratoId = UUID.randomUUID();
        when(agendaPort.buscarAtivaPorContrato(contratoId)).thenReturn(Optional.empty());
        when(agendaPort.salvarEFlush(any(AgendaPagamento.class))).thenAnswer(inv -> inv.getArgument(0));

        AgendaPagamento agenda = useCase.executar(new GerarAgendaPagamentoCommand(
                contratoId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("50000"),
                24,
                new BigDecimal("0.015"),
                LocalDate.of(2026, 5, 1),
                SistemaAmortizacao.PRICE));

        assertThat(agenda.getParcelas()).hasSize(24);
    }

    @Test
    void idempotente_segunda_execucao_retorna_existente_sem_publicar() {
        UUID contratoId = UUID.randomUUID();
        AgendaPagamento existente = AgendaPagamento.criar(
                contratoId,
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100")), LocalDate.of(2026, 6, 1))));
        when(agendaPort.buscarAtivaPorContrato(contratoId)).thenReturn(Optional.of(existente));

        AgendaPagamento ret = useCase.executar(new GerarAgendaPagamentoCommand(
                contratoId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("12000"),
                12,
                new BigDecimal("0.02"),
                LocalDate.of(2026, 5, 1),
                SistemaAmortizacao.PRICE));

        assertThat(ret).isSameAs(existente);
        verify(agendaPort, never()).salvarEFlush(any(AgendaPagamento.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void corrida_concorrente_unique_constraint_retorna_existente() {
        UUID contratoId = UUID.randomUUID();
        AgendaPagamento existente = AgendaPagamento.criar(
                contratoId,
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100")), LocalDate.of(2026, 6, 1))));
        // Primeira chamada (check inicial) volta vazia; segunda chamada (apos DIVE) volta com o
        // registro persistido pela outra transacao.
        when(agendaPort.buscarAtivaPorContrato(contratoId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existente));
        when(agendaPort.salvarEFlush(any(AgendaPagamento.class)))
                .thenThrow(new DataIntegrityViolationException("uq_contrato_id violou"));

        AgendaPagamento ret = useCase.executar(new GerarAgendaPagamentoCommand(
                contratoId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("12000"),
                12,
                new BigDecimal("0.02"),
                LocalDate.of(2026, 5, 1),
                SistemaAmortizacao.PRICE));

        assertThat(ret).isSameAs(existente);
        // Evento nao deve ser publicado em corrida — outra transacao ja publicou.
        verify(eventPublisher, never()).publishEvent(any());
        verify(agendaPort, times(2)).buscarAtivaPorContrato(eq(contratoId));
    }
}
