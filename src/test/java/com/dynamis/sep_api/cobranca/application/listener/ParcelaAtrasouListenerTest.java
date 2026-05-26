package com.dynamis.sep_api.cobranca.application.listener;

import com.dynamis.sep_api.cobranca.application.dto.EscalarCobrancaCommand;
import com.dynamis.sep_api.cobranca.application.dto.EscalonamentoResult;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.usecase.EscalarCobrancaUseCase;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaAtrasouEvent;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParcelaAtrasouListenerTest {

    private EscalarCobrancaUseCase useCase;
    private ContratoCobrancaQueryPort contratoQuery;
    private UsuarioRepository usuarioRepository;
    private ParcelaCobrancaRepository parcelaRepository;
    private ParcelaAtrasouListener listener;

    @BeforeEach
    void setup() {
        useCase = mock(EscalarCobrancaUseCase.class);
        contratoQuery = mock(ContratoCobrancaQueryPort.class);
        usuarioRepository = mock(UsuarioRepository.class);
        parcelaRepository = mock(ParcelaCobrancaRepository.class);
        when(useCase.escalar(any())).thenReturn(new EscalonamentoResult(true, false, false, false, 1));
        listener = new ParcelaAtrasouListener(useCase, contratoQuery, usuarioRepository, parcelaRepository);
    }

    @Test
    void aoAtrasarParcela_dispatchEscalarComDiasZero() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        UUID parcelaId = UUID.randomUUID();
        when(contratoQuery.tomadorIdDoContrato(contratoId)).thenReturn(Optional.of(tomadorId));
        Usuario usuario = Usuario.criar("tomador@example.com", "hash", Role.CLIENTE);
        when(usuarioRepository.findById(tomadorId)).thenReturn(Optional.of(usuario));
        ParcelaCobranca parcela = parcelaMock();
        when(parcelaRepository.findById(parcelaId)).thenReturn(Optional.of(parcela));

        listener.aoAtrasarParcela(
                new ParcelaAtrasouEvent(parcelaId, UUID.randomUUID(), contratoId, 3, LocalDate.of(2026, 6, 1)));

        ArgumentCaptor<EscalarCobrancaCommand> captor = ArgumentCaptor.forClass(EscalarCobrancaCommand.class);
        verify(useCase).escalar(captor.capture());
        EscalarCobrancaCommand cmd = captor.getValue();
        assertThat(cmd.parcelaId()).isEqualTo(parcelaId);
        assertThat(cmd.diasAtraso()).isZero();
        assertThat(cmd.emailTomador()).isEqualTo("tomador@example.com");
        assertThat(cmd.telefoneTomador()).isNull();
        assertThat(cmd.variaveis()).containsEntry("numeroParcela", 3);
        assertThat(cmd.variaveis()).containsEntry("dataVencimento", "01/06/2026");
    }

    @Test
    void semTomadorId_naoChamaUseCase() {
        UUID contratoId = UUID.randomUUID();
        when(contratoQuery.tomadorIdDoContrato(contratoId)).thenReturn(Optional.empty());

        listener.aoAtrasarParcela(
                new ParcelaAtrasouEvent(UUID.randomUUID(), UUID.randomUUID(), contratoId, 1, LocalDate.of(2026, 6, 1)));

        verify(useCase, never()).escalar(any());
    }

    @Test
    void semUsuario_chamaUseCaseComEmailNull() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        UUID parcelaId = UUID.randomUUID();
        when(contratoQuery.tomadorIdDoContrato(contratoId)).thenReturn(Optional.of(tomadorId));
        when(usuarioRepository.findById(tomadorId)).thenReturn(Optional.empty());
        when(parcelaRepository.findById(parcelaId)).thenReturn(Optional.empty());

        listener.aoAtrasarParcela(
                new ParcelaAtrasouEvent(parcelaId, UUID.randomUUID(), contratoId, 1, LocalDate.of(2026, 6, 1)));

        ArgumentCaptor<EscalarCobrancaCommand> captor = ArgumentCaptor.forClass(EscalarCobrancaCommand.class);
        verify(useCase).escalar(captor.capture());
        assertThat(captor.getValue().emailTomador()).isNull();
    }

    private static ParcelaCobranca parcelaMock() {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("250.00")), LocalDate.of(2026, 6, 1))));
        return agenda.getParcelas().get(0);
    }
}
