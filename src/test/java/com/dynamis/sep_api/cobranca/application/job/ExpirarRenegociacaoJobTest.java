package com.dynamis.sep_api.cobranca.application.job;

import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoRecusadaEvent;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RenegociacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpirarRenegociacaoJobTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-20T10:00:00Z"), ZoneOffset.UTC);

    private RenegociacaoRepository renegociacaoRepository;
    private ParcelaCobrancaRepository parcelaRepository;
    private ApplicationEventPublisher eventPublisher;
    private TransactionTemplate txTemplate;
    private ExpirarRenegociacaoJob job;

    @BeforeEach
    void setup() {
        renegociacaoRepository = mock(RenegociacaoRepository.class);
        parcelaRepository = mock(ParcelaCobrancaRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        txTemplate = mock(TransactionTemplate.class);
        when(txTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<Object> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        when(renegociacaoRepository.save(any(Renegociacao.class))).thenAnswer(inv -> inv.getArgument(0));
        job = new ExpirarRenegociacaoJob(renegociacaoRepository, parcelaRepository, eventPublisher, txTemplate, CLOCK);
    }

    @Test
    void executar_propostasExpiradas_marcaEXPIRADAEVolaParcelaParaStatusAnterior() {
        ParcelaCobranca parcela = parcelaEmNegociacao();
        Renegociacao renegociacao = renegociacaoExpirada(parcela);
        when(renegociacaoRepository.findByStatusAndDataExpiracaoBefore(any(), any()))
                .thenReturn(List.of(renegociacao));
        when(renegociacaoRepository.findById(renegociacao.getId())).thenReturn(Optional.of(renegociacao));
        when(parcelaRepository.findByIdForUpdate(parcela.getId())).thenReturn(Optional.of(parcela));

        int processadas = job.executar();

        assertThat(processadas).isEqualTo(1);
        assertThat(renegociacao.getStatus()).isEqualTo(StatusRenegociacao.EXPIRADA);
        assertThat(parcela.getStatus()).isEqualTo(StatusParcela.ATRASADA);
        verify(eventPublisher).publishEvent(any(RenegociacaoRecusadaEvent.class));
    }

    @Test
    void executar_jaDecidida_pula() {
        ParcelaCobranca parcela = parcelaEmNegociacao();
        Renegociacao renegociacao = renegociacaoExpirada(parcela);
        renegociacao.aceitar(UUID.randomUUID(), OffsetDateTime.now(CLOCK));
        when(renegociacaoRepository.findByStatusAndDataExpiracaoBefore(any(), any()))
                .thenReturn(List.of(renegociacao));
        when(renegociacaoRepository.findById(renegociacao.getId())).thenReturn(Optional.of(renegociacao));

        int processadas = job.executar();

        assertThat(processadas).isZero();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void executar_parcelaSumiuAntesDoLock_pula() {
        ParcelaCobranca parcela = parcelaEmNegociacao();
        Renegociacao renegociacao = renegociacaoExpirada(parcela);
        when(renegociacaoRepository.findByStatusAndDataExpiracaoBefore(any(), any()))
                .thenReturn(List.of(renegociacao));
        when(renegociacaoRepository.findById(renegociacao.getId())).thenReturn(Optional.of(renegociacao));
        when(parcelaRepository.findByIdForUpdate(parcela.getId())).thenReturn(Optional.empty());

        int processadas = job.executar();

        assertThat(processadas).isZero();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void executar_falhaIndividual_naoQuebraLoop() {
        ParcelaCobranca p1 = parcelaEmNegociacao();
        ParcelaCobranca p2 = parcelaEmNegociacao();
        Renegociacao r1 = renegociacaoExpirada(p1);
        Renegociacao r2 = renegociacaoExpirada(p2);
        when(renegociacaoRepository.findByStatusAndDataExpiracaoBefore(any(), any()))
                .thenReturn(List.of(r1, r2));
        when(renegociacaoRepository.findById(r1.getId())).thenThrow(new RuntimeException("boom"));
        when(renegociacaoRepository.findById(r2.getId())).thenReturn(Optional.of(r2));
        when(parcelaRepository.findByIdForUpdate(p2.getId())).thenReturn(Optional.of(p2));

        int processadas = job.executar();

        assertThat(processadas).isEqualTo(1);
        assertThat(r2.getStatus()).isEqualTo(StatusRenegociacao.EXPIRADA);
    }

    private static ParcelaCobranca parcelaEmNegociacao() {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 3, 15))));
        ParcelaCobranca parcela = agenda.getParcelas().get(0);
        parcela.marcarAtrasada();
        parcela.iniciarNegociacao();
        return parcela;
    }

    private static Renegociacao renegociacaoExpirada(ParcelaCobranca parcela) {
        OffsetDateTime passado = OffsetDateTime.now(CLOCK).minusDays(10);
        return Renegociacao.propor(
                parcela.getId(),
                parcela.getAgenda().getId(),
                UUID.randomUUID(),
                StatusParcela.ATRASADA,
                new BigDecimal("110.00"),
                LocalDate.of(2026, 7, 10),
                3,
                BigDecimal.ZERO,
                "Acordo",
                UUID.randomUUID(),
                passado,
                passado.plusDays(7));
    }
}
