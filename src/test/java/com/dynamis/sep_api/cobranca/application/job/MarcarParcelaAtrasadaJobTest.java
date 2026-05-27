package com.dynamis.sep_api.cobranca.application.job;

import com.dynamis.sep_api.cobranca.domain.event.ParcelaAtrasouEvent;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.shared.infrastructure.job.PostgresAdvisoryJobLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.context.ApplicationEventPublisher;

import java.util.function.IntSupplier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarcarParcelaAtrasadaJobTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private ParcelaCobrancaRepository parcelaRepository;
    private ApplicationEventPublisher eventPublisher;
    private PostgresAdvisoryJobLock jobLock;
    private Clock clock;
    private MarcarParcelaAtrasadaJob job;

    @BeforeEach
    void setup() {
        parcelaRepository = mock(ParcelaCobrancaRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        jobLock = mock(PostgresAdvisoryJobLock.class);
        when(jobLock.runIfAcquired(org.mockito.ArgumentMatchers.eq(MarcarParcelaAtrasadaJob.LOCK_KEY),
                        org.mockito.ArgumentMatchers.any(IntSupplier.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.<IntSupplier>getArgument(1).getAsInt());
        clock = Clock.fixed(LocalDate.of(2026, 7, 15).atStartOfDay(SP).toInstant(), SP);
        job = new MarcarParcelaAtrasadaJob(parcelaRepository, eventPublisher, jobLock, clock);
    }

    @Test
    void marcaPendentesVencidasEPublicaEvento() {
        ParcelaCobranca p1 = novaParcela(1, LocalDate.of(2026, 6, 1));
        ParcelaCobranca p2 = novaParcela(2, LocalDate.of(2026, 7, 1));
        when(parcelaRepository.findByStatusAndDataVencimentoBefore(StatusParcela.PENDENTE, LocalDate.of(2026, 7, 15)))
                .thenReturn(List.of(p1, p2));

        int marcadas = job.executar();

        assertThat(marcadas).isEqualTo(2);
        assertThat(p1.getStatus()).isEqualTo(StatusParcela.ATRASADA);
        assertThat(p2.getStatus()).isEqualTo(StatusParcela.ATRASADA);
        verify(eventPublisher, times(2)).publishEvent(org.mockito.ArgumentMatchers.any(ParcelaAtrasouEvent.class));
    }

    @Test
    void reexecucao_naoRepublicaEventoParaParcelaJaAtrasada() {
        // 1a execucao
        ParcelaCobranca p = novaParcela(1, LocalDate.of(2026, 6, 1));
        when(parcelaRepository.findByStatusAndDataVencimentoBefore(StatusParcela.PENDENTE, LocalDate.of(2026, 7, 15)))
                .thenReturn(List.of(p))
                .thenReturn(List.of()); // 2a execucao: query exclui ja-ATRASADA pelo filtro PENDENTE

        int primeira = job.executar();
        int segunda = job.executar();

        assertThat(primeira).isEqualTo(1);
        assertThat(segunda).isEqualTo(0);
        // Evento publicado apenas uma vez
        verify(eventPublisher, times(1)).publishEvent(org.mockito.ArgumentMatchers.any(ParcelaAtrasouEvent.class));
    }

    @Test
    void semParcelasVencidas_naoPublicaEvento() {
        when(parcelaRepository.findByStatusAndDataVencimentoBefore(StatusParcela.PENDENTE, LocalDate.of(2026, 7, 15)))
                .thenReturn(List.of());

        int marcadas = job.executar();

        assertThat(marcadas).isZero();
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void quandoLockNaoAdquirido_naoExecutaEDevolveZero() {
        when(jobLock.runIfAcquired(org.mockito.ArgumentMatchers.eq(MarcarParcelaAtrasadaJob.LOCK_KEY),
                        org.mockito.ArgumentMatchers.any(IntSupplier.class)))
                .thenReturn(0);

        int marcadas = job.executar();

        assertThat(marcadas).isZero();
        verify(parcelaRepository, never()).findByStatusAndDataVencimentoBefore(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private static ParcelaCobranca novaParcela(int numero, LocalDate vencimento) {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(new ParcelaPlanejada(
                        numero, ComposicaoValor.principalApenas(new BigDecimal("100.00")), vencimento)));
        return agenda.getParcelas().get(0);
    }
}
