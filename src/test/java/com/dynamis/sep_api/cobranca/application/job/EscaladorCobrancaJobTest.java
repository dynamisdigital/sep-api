package com.dynamis.sep_api.cobranca.application.job;

import com.dynamis.sep_api.cobranca.application.dto.EscalarCobrancaCommand;
import com.dynamis.sep_api.cobranca.application.dto.EscalonamentoResult;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.usecase.EscalarCobrancaUseCase;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

class EscaladorCobrancaJobTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-20T10:00:00Z"), ZoneOffset.UTC);

    private ParcelaCobrancaRepository parcelaRepository;
    private ContratoCobrancaQueryPort contratoQuery;
    private UsuarioRepository usuarioRepository;
    private EscalarCobrancaUseCase useCase;
    private EscaladorCobrancaJob job;

    @BeforeEach
    void setup() {
        parcelaRepository = mock(ParcelaCobrancaRepository.class);
        contratoQuery = mock(ContratoCobrancaQueryPort.class);
        usuarioRepository = mock(UsuarioRepository.class);
        useCase = mock(EscalarCobrancaUseCase.class);
        when(useCase.escalar(any())).thenReturn(new EscalonamentoResult(true, false, false, false, 1));
        job = new EscaladorCobrancaJob(parcelaRepository, contratoQuery, usuarioRepository, useCase, CLOCK);
    }

    @Test
    void executar_calculaDiasAtrasoEDispachaPorParcela() {
        // Parcela vence 2026-06-15; hoje (do clock) eh 2026-06-20 -> 5 dias.
        ParcelaCobranca parcela = parcelaCom(LocalDate.of(2026, 6, 15));
        when(parcelaRepository.findComAgendaByStatusAndDataVencimentoBefore(any(), any()))
                .thenReturn(List.of(parcela));
        UUID tomadorId = UUID.randomUUID();
        when(contratoQuery.tomadorIdDoContrato(parcela.getAgenda().getContratoId()))
                .thenReturn(Optional.of(tomadorId));
        Usuario u = Usuario.criar("tomador@example.com", "hash", Role.CLIENTE);
        when(usuarioRepository.findById(tomadorId)).thenReturn(Optional.of(u));

        int processadas = job.executar();

        assertThat(processadas).isEqualTo(1);
        ArgumentCaptor<EscalarCobrancaCommand> captor = ArgumentCaptor.forClass(EscalarCobrancaCommand.class);
        verify(useCase).escalar(captor.capture());
        EscalarCobrancaCommand cmd = captor.getValue();
        assertThat(cmd.diasAtraso()).isEqualTo(5);
        assertThat(cmd.emailTomador()).isEqualTo("tomador@example.com");
    }

    @Test
    void executar_diasNaoPositivo_pula() {
        // Mesmo dia do vencimento -> dias=0; cobertura desse caso fica com listener (Sprint 12).
        ParcelaCobranca parcela = parcelaCom(LocalDate.of(2026, 6, 20));
        when(parcelaRepository.findComAgendaByStatusAndDataVencimentoBefore(any(), any()))
                .thenReturn(List.of(parcela));

        int processadas = job.executar();

        assertThat(processadas).isZero();
        verify(useCase, never()).escalar(any());
    }

    @Test
    void executar_semTomadorId_logaEPula() {
        ParcelaCobranca parcela = parcelaCom(LocalDate.of(2026, 6, 5));
        when(parcelaRepository.findComAgendaByStatusAndDataVencimentoBefore(any(), any()))
                .thenReturn(List.of(parcela));
        when(contratoQuery.tomadorIdDoContrato(any())).thenReturn(Optional.empty());

        int processadas = job.executar();

        assertThat(processadas).isEqualTo(1);
        verify(useCase, never()).escalar(any());
    }

    @Test
    void executar_falhaUseCase_naoQuebraLoop() {
        ParcelaCobranca p1 = parcelaCom(LocalDate.of(2026, 6, 5));
        ParcelaCobranca p2 = parcelaCom(LocalDate.of(2026, 6, 10));
        when(parcelaRepository.findComAgendaByStatusAndDataVencimentoBefore(any(), any()))
                .thenReturn(List.of(p1, p2));
        when(contratoQuery.tomadorIdDoContrato(any())).thenReturn(Optional.of(UUID.randomUUID()));
        when(usuarioRepository.findById(any())).thenReturn(Optional.of(Usuario.criar("x@y.com", "h", Role.CLIENTE)));
        when(useCase.escalar(any()))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(new EscalonamentoResult(true, false, false, false, 1));

        int processadas = job.executar();

        assertThat(processadas).isEqualTo(1); // p1 falhou, p2 processou
        verify(useCase, org.mockito.Mockito.times(2)).escalar(any());
    }

    @Test
    void executar_buscaApenasAtrasadas() {
        when(parcelaRepository.findComAgendaByStatusAndDataVencimentoBefore(any(), any()))
                .thenReturn(List.of());

        job.executar();

        verify(parcelaRepository)
                .findComAgendaByStatusAndDataVencimentoBefore(org.mockito.Mockito.eq(StatusParcela.ATRASADA), any());
    }

    private static ParcelaCobranca parcelaCom(LocalDate dataVencimento) {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), dataVencimento)));
        ParcelaCobranca parcela = agenda.getParcelas().get(0);
        parcela.marcarAtrasada();
        return parcela;
    }
}
