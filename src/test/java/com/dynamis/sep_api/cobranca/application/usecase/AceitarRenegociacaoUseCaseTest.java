package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.port.out.AgendaPagamentoCobrancaPort;
import com.dynamis.sep_api.cobranca.application.port.out.ParcelaCobrancaPort;
import com.dynamis.sep_api.cobranca.application.port.out.RenegociacaoCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoAceitaEvent;
import com.dynamis.sep_api.cobranca.domain.exception.CobrancaOwnershipException;
import com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
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

class AceitarRenegociacaoUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-20T10:00:00Z"), ZoneOffset.UTC);

    private RenegociacaoCobrancaPort renegociacaoPort;
    private ParcelaCobrancaPort parcelaPort;
    private AgendaPagamentoCobrancaPort agendaPort;
    private ApplicationEventPublisher eventPublisher;
    private AceitarRenegociacaoUseCase useCase;
    private UUID tomadorId;

    @BeforeEach
    void setup() {
        renegociacaoPort = mock(RenegociacaoCobrancaPort.class);
        parcelaPort = mock(ParcelaCobrancaPort.class);
        agendaPort = mock(AgendaPagamentoCobrancaPort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        tomadorId = UUID.randomUUID();
        when(agendaPort.salvarEFlush(any(AgendaPagamento.class))).thenAnswer(inv -> inv.getArgument(0));
        when(agendaPort.salvar(any(AgendaPagamento.class))).thenAnswer(inv -> inv.getArgument(0));
        when(renegociacaoPort.salvar(any(Renegociacao.class))).thenAnswer(inv -> inv.getArgument(0));
        useCase = new AceitarRenegociacaoUseCase(renegociacaoPort, parcelaPort, agendaPort, eventPublisher, CLOCK);
    }

    @Test
    void executar_aceiteValido_geraAgendaSubstitutaEMarcaParcelaRenegociada() {
        ParcelaCobranca parcela = parcelaEmNegociacao();
        Renegociacao renegociacao = renegociacaoPara(parcela);
        when(renegociacaoPort.buscarPorIdComLock(renegociacao.getId())).thenReturn(Optional.of(renegociacao));
        when(parcelaPort.buscarPorIdComLock(parcela.getId())).thenReturn(Optional.of(parcela));

        Renegociacao decidida = useCase.executar(renegociacao.getId(), tomadorId);

        assertThat(decidida.getStatus()).isEqualTo(StatusRenegociacao.ACEITA);
        assertThat(decidida.getAgendaSubstitutaId()).isNotNull();
        assertThat(parcela.getStatus()).isEqualTo(StatusParcela.RENEGOCIADA);
        assertThat(parcela.getAgenda().isAtiva()).isFalse();
        verify(eventPublisher).publishEvent(any(RenegociacaoAceitaEvent.class));
    }

    @Test
    void executar_ownerInvalido_rejeita() {
        ParcelaCobranca parcela = parcelaEmNegociacao();
        Renegociacao renegociacao = renegociacaoPara(parcela);
        when(renegociacaoPort.buscarPorIdComLock(renegociacao.getId())).thenReturn(Optional.of(renegociacao));

        assertThatThrownBy(() -> useCase.executar(renegociacao.getId(), UUID.randomUUID()))
                .isInstanceOf(CobrancaOwnershipException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void executar_renegociacaoExpirada_rejeita() {
        ParcelaCobranca parcela = parcelaEmNegociacao();
        Renegociacao renegociacao = renegociacaoExpiradaPara(parcela);
        when(renegociacaoPort.buscarPorIdComLock(renegociacao.getId())).thenReturn(Optional.of(renegociacao));

        assertThatThrownBy(() -> useCase.executar(renegociacao.getId(), tomadorId))
                .isInstanceOf(com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoEstadoInvalidoException.class)
                .hasMessageContaining("EXPIRADA");
    }

    @Test
    void executar_renegociacaoJaDecidida_rejeita() {
        ParcelaCobranca parcela = parcelaEmNegociacao();
        Renegociacao renegociacao = renegociacaoPara(parcela);
        renegociacao.recusar(OffsetDateTime.now(CLOCK));
        when(renegociacaoPort.buscarPorIdComLock(renegociacao.getId())).thenReturn(Optional.of(renegociacao));

        assertThatThrownBy(() -> useCase.executar(renegociacao.getId(), tomadorId))
                .isInstanceOf(com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoEstadoInvalidoException.class)
                .hasMessageContaining("RECUSADA");
    }

    @Test
    void executar_renegociacaoInexistente_rejeita() {
        UUID id = UUID.randomUUID();
        when(renegociacaoPort.buscarPorIdComLock(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(id, tomadorId))
                .isInstanceOf(RenegociacaoNaoEncontradaException.class);
    }

    @Test
    void executar_novaAgendaTemNumeroParcelasCorreto() {
        ParcelaCobranca parcela = parcelaEmNegociacao();
        Renegociacao renegociacao = renegociacaoPara(parcela); // 3 parcelas
        when(renegociacaoPort.buscarPorIdComLock(renegociacao.getId())).thenReturn(Optional.of(renegociacao));
        when(parcelaPort.buscarPorIdComLock(parcela.getId())).thenReturn(Optional.of(parcela));

        useCase.executar(renegociacao.getId(), tomadorId);

        org.mockito.ArgumentCaptor<AgendaPagamento> captor = org.mockito.ArgumentCaptor.forClass(AgendaPagamento.class);
        verify(agendaPort).salvar(captor.capture());
        AgendaPagamento substituta = captor.getValue();
        assertThat(substituta.getNumeroParcelas()).isEqualTo(3);
        assertThat(substituta.getParcelas()).hasSize(3);
        assertThat(substituta.getAgendaSubstituidaId())
                .isEqualTo(parcela.getAgenda().getId());
    }

    private ParcelaCobranca parcelaEmNegociacao() {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 3, 15))));
        ParcelaCobranca parcela = agenda.getParcelas().get(0);
        parcela.marcarAtrasada();
        parcela.iniciarNegociacao();
        return parcela;
    }

    private Renegociacao renegociacaoPara(ParcelaCobranca parcela) {
        return Renegociacao.propor(
                parcela.getId(),
                parcela.getAgenda().getId(),
                tomadorId,
                StatusParcela.ATRASADA,
                new BigDecimal("110.00"),
                LocalDate.of(2026, 7, 10),
                3,
                BigDecimal.ZERO,
                "Acordo",
                UUID.randomUUID(),
                OffsetDateTime.now(CLOCK),
                OffsetDateTime.now(CLOCK).plusDays(7));
    }

    private Renegociacao renegociacaoExpiradaPara(ParcelaCobranca parcela) {
        OffsetDateTime passado = OffsetDateTime.now(CLOCK).minusDays(10);
        return Renegociacao.propor(
                parcela.getId(),
                parcela.getAgenda().getId(),
                tomadorId,
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
