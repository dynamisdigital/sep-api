package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.IniciarRenegociacaoCommand;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.port.out.ParcelaCobrancaPort;
import com.dynamis.sep_api.cobranca.application.port.out.RenegociacaoCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoPropostaEvent;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaCobrancaNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaEstadoInvalidoException;
import com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoConflitanteException;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

class IniciarRenegociacaoUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-20T10:00:00Z"), ZoneOffset.UTC);

    private ParcelaCobrancaPort parcelaPort;
    private RenegociacaoCobrancaPort renegociacaoPort;
    private ContratoCobrancaQueryPort contratoQuery;
    private ApplicationEventPublisher eventPublisher;
    private IniciarRenegociacaoUseCase useCase;
    private UUID tomadorId;
    private UUID financeiroId;

    @BeforeEach
    void setup() {
        parcelaPort = mock(ParcelaCobrancaPort.class);
        renegociacaoPort = mock(RenegociacaoCobrancaPort.class);
        contratoQuery = mock(ContratoCobrancaQueryPort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        tomadorId = UUID.randomUUID();
        financeiroId = UUID.randomUUID();
        when(contratoQuery.tomadorIdDoContrato(any())).thenReturn(Optional.of(tomadorId));
        when(renegociacaoPort.salvarEFlush(any(Renegociacao.class))).thenAnswer(inv -> inv.getArgument(0));
        useCase = new IniciarRenegociacaoUseCase(parcelaPort, renegociacaoPort, contratoQuery, eventPublisher, CLOCK);
    }

    @Test
    void executar_parcelaAtrasada_proposeRenegociacaoEMudaStatus() {
        ParcelaCobranca parcela = parcelaCom(StatusParcela.ATRASADA);
        when(parcelaPort.buscarPorIdComLock(parcela.getId())).thenReturn(Optional.of(parcela));

        Renegociacao r = useCase.executar(comando(parcela.getId()));

        assertThat(parcela.getStatus()).isEqualTo(StatusParcela.EM_NEGOCIACAO);
        assertThat(r.getStatus()).isEqualTo(StatusRenegociacao.PROPOSTA);
        assertThat(r.getStatusParcelaAnterior()).isEqualTo(StatusParcela.ATRASADA);
        assertThat(r.getTomadorId()).isEqualTo(tomadorId);
        assertThat(r.getDataExpiracao()).isEqualTo(r.getDataProposta().plusDays(7));
        verify(eventPublisher).publishEvent(any(RenegociacaoPropostaEvent.class));
        verify(parcelaPort).salvar(parcela);
    }

    @Test
    void executar_parcelaInadimplente_proposeOK() {
        ParcelaCobranca parcela = parcelaCom(StatusParcela.INADIMPLENTE);
        when(parcelaPort.buscarPorIdComLock(parcela.getId())).thenReturn(Optional.of(parcela));

        Renegociacao r = useCase.executar(comando(parcela.getId()));

        assertThat(r.getStatusParcelaAnterior()).isEqualTo(StatusParcela.INADIMPLENTE);
        assertThat(parcela.getStatus()).isEqualTo(StatusParcela.EM_NEGOCIACAO);
    }

    @Test
    void executar_parcelaPendente_rejeita() {
        ParcelaCobranca parcela = parcelaCom(StatusParcela.PENDENTE);
        when(parcelaPort.buscarPorIdComLock(parcela.getId())).thenReturn(Optional.of(parcela));

        assertThatThrownBy(() -> useCase.executar(comando(parcela.getId())))
                .isInstanceOf(ParcelaEstadoInvalidoException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void executar_parcelaInexistente_rejeita() {
        UUID parcelaId = UUID.randomUUID();
        when(parcelaPort.buscarPorIdComLock(parcelaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(comando(parcelaId)))
                .isInstanceOf(ParcelaCobrancaNaoEncontradaException.class);
    }

    @Test
    void executar_jaTemPropostaAtiva_rejeita() {
        ParcelaCobranca parcela = parcelaCom(StatusParcela.ATRASADA);
        when(parcelaPort.buscarPorIdComLock(parcela.getId())).thenReturn(Optional.of(parcela));
        when(renegociacaoPort.existePorParcelaOriginalEStatus(parcela.getId(), StatusRenegociacao.PROPOSTA))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(comando(parcela.getId())))
                .isInstanceOf(RenegociacaoConflitanteException.class);
        assertThat(parcela.getStatus()).isEqualTo(StatusParcela.ATRASADA); // nao mudou
    }

    @Test
    void executar_raceUniqueParcial_converteParaConflitante() {
        ParcelaCobranca parcela = parcelaCom(StatusParcela.ATRASADA);
        when(parcelaPort.buscarPorIdComLock(parcela.getId())).thenReturn(Optional.of(parcela));
        when(renegociacaoPort.salvarEFlush(any(Renegociacao.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement; constraint [uq_renegociacao_parcela_ativa]"));

        assertThatThrownBy(() -> useCase.executar(comando(parcela.getId())))
                .isInstanceOf(RenegociacaoConflitanteException.class);
    }

    @Test
    void executar_outraDataIntegrityViolation_naoMascaraComoConflitante() {
        // Hotfix code review: catch genérico converteria todas as DIV em RenegociacaoConflitante.
        // Filtra pelo nome do constraint pra deixar outras violacoes (FK, check, NOT NULL) subirem.
        ParcelaCobranca parcela = parcelaCom(StatusParcela.ATRASADA);
        when(parcelaPort.buscarPorIdComLock(parcela.getId())).thenReturn(Optional.of(parcela));
        when(renegociacaoPort.salvarEFlush(any(Renegociacao.class)))
                .thenThrow(new DataIntegrityViolationException("constraint [fk_renegociacao_agenda] violated"));

        assertThatThrownBy(() -> useCase.executar(comando(parcela.getId())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_renegociacao_agenda");
    }

    @Test
    void executar_semTomadorId_falha() {
        ParcelaCobranca parcela = parcelaCom(StatusParcela.ATRASADA);
        when(parcelaPort.buscarPorIdComLock(parcela.getId())).thenReturn(Optional.of(parcela));
        when(contratoQuery.tomadorIdDoContrato(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(comando(parcela.getId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem tomadorId");
    }

    private IniciarRenegociacaoCommand comando(UUID parcelaId) {
        return new IniciarRenegociacaoCommand(
                parcelaId,
                new BigDecimal("120.00"),
                LocalDate.of(2026, 7, 10),
                3,
                BigDecimal.ZERO,
                "Cliente solicitou acordo",
                financeiroId);
    }

    private static ParcelaCobranca parcelaCom(StatusParcela status) {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 6, 1))));
        ParcelaCobranca parcela = agenda.getParcelas().get(0);
        if (status == StatusParcela.ATRASADA) {
            parcela.marcarAtrasada();
        } else if (status == StatusParcela.INADIMPLENTE) {
            parcela.marcarAtrasada();
            parcela.marcarInadimplente();
        }
        return parcela;
    }
}
