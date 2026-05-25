package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoRecusadaEvent;
import com.dynamis.sep_api.cobranca.domain.exception.CobrancaOwnershipException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecusarRenegociacaoUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-20T10:00:00Z"), ZoneOffset.UTC);

    private RenegociacaoRepository renegociacaoRepository;
    private ParcelaCobrancaRepository parcelaRepository;
    private ApplicationEventPublisher eventPublisher;
    private RecusarRenegociacaoUseCase useCase;
    private UUID tomadorId;

    @BeforeEach
    void setup() {
        renegociacaoRepository = mock(RenegociacaoRepository.class);
        parcelaRepository = mock(ParcelaCobrancaRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        tomadorId = UUID.randomUUID();
        when(renegociacaoRepository.save(any(Renegociacao.class))).thenAnswer(inv -> inv.getArgument(0));
        useCase = new RecusarRenegociacaoUseCase(renegociacaoRepository, parcelaRepository, eventPublisher, CLOCK);
    }

    @Test
    void executar_recusaValida_parcelaVoltaParaStatusAnterior() {
        ParcelaCobranca parcela = parcelaEmNegociacao(StatusParcela.ATRASADA);
        Renegociacao renegociacao = renegociacaoPara(parcela, StatusParcela.ATRASADA);
        when(renegociacaoRepository.findById(renegociacao.getId())).thenReturn(Optional.of(renegociacao));
        when(parcelaRepository.findByIdForUpdate(parcela.getId())).thenReturn(Optional.of(parcela));

        Renegociacao decidida = useCase.executar(renegociacao.getId(), tomadorId);

        assertThat(decidida.getStatus()).isEqualTo(StatusRenegociacao.RECUSADA);
        assertThat(parcela.getStatus()).isEqualTo(StatusParcela.ATRASADA);
        verify(eventPublisher).publishEvent(any(RenegociacaoRecusadaEvent.class));
    }

    @Test
    void executar_recusaParcelaInadimplente_voltaParaInadimplente() {
        ParcelaCobranca parcela = parcelaEmNegociacao(StatusParcela.INADIMPLENTE);
        Renegociacao renegociacao = renegociacaoPara(parcela, StatusParcela.INADIMPLENTE);
        when(renegociacaoRepository.findById(renegociacao.getId())).thenReturn(Optional.of(renegociacao));
        when(parcelaRepository.findByIdForUpdate(parcela.getId())).thenReturn(Optional.of(parcela));

        useCase.executar(renegociacao.getId(), tomadorId);

        assertThat(parcela.getStatus()).isEqualTo(StatusParcela.INADIMPLENTE);
    }

    @Test
    void executar_ownerInvalido_rejeita() {
        ParcelaCobranca parcela = parcelaEmNegociacao(StatusParcela.ATRASADA);
        Renegociacao renegociacao = renegociacaoPara(parcela, StatusParcela.ATRASADA);
        when(renegociacaoRepository.findById(renegociacao.getId())).thenReturn(Optional.of(renegociacao));

        assertThatThrownBy(() -> useCase.executar(renegociacao.getId(), UUID.randomUUID()))
                .isInstanceOf(CobrancaOwnershipException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void executar_renegociacaoJaDecidida_rejeita() {
        ParcelaCobranca parcela = parcelaEmNegociacao(StatusParcela.ATRASADA);
        Renegociacao renegociacao = renegociacaoPara(parcela, StatusParcela.ATRASADA);
        renegociacao.aceitar(UUID.randomUUID(), OffsetDateTime.now(CLOCK));
        when(renegociacaoRepository.findById(renegociacao.getId())).thenReturn(Optional.of(renegociacao));

        assertThatThrownBy(() -> useCase.executar(renegociacao.getId(), tomadorId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACEITA");
    }

    private static ParcelaCobranca parcelaEmNegociacao(StatusParcela statusAnterior) {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 3, 15))));
        ParcelaCobranca parcela = agenda.getParcelas().get(0);
        parcela.marcarAtrasada();
        if (statusAnterior == StatusParcela.INADIMPLENTE) {
            parcela.marcarInadimplente();
        }
        parcela.iniciarNegociacao();
        return parcela;
    }

    private Renegociacao renegociacaoPara(ParcelaCobranca parcela, StatusParcela statusAnterior) {
        return Renegociacao.propor(
                parcela.getId(),
                parcela.getAgenda().getId(),
                tomadorId,
                statusAnterior,
                new BigDecimal("110.00"),
                LocalDate.of(2026, 7, 10),
                3,
                BigDecimal.ZERO,
                "Acordo",
                UUID.randomUUID(),
                OffsetDateTime.now(CLOCK),
                OffsetDateTime.now(CLOCK).plusDays(7));
    }
}
