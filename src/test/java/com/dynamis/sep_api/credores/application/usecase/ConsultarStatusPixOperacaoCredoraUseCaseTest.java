package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.PixOperacaoStatusView;
import com.dynamis.sep_api.credores.application.port.out.PixOperacaoStatusQueryPort;
import com.dynamis.sep_api.credores.domain.exception.StatusPixOperacaoNaoEncontradoException;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConsultarStatusPixOperacaoCredoraUseCaseTest {

    private EmpresaCredoraRepository empresaRepository;
    private OperacaoFinanciadaRepository operacaoRepository;
    private PixOperacaoStatusQueryPort pixOperacaoStatusPort;
    private ConsultarStatusPixOperacaoCredoraUseCase useCase;

    @BeforeEach
    void setup() {
        empresaRepository = mock(EmpresaCredoraRepository.class);
        operacaoRepository = mock(OperacaoFinanciadaRepository.class);
        pixOperacaoStatusPort = mock(PixOperacaoStatusQueryPort.class);
        useCase = new ConsultarStatusPixOperacaoCredoraUseCase(
                empresaRepository, operacaoRepository, pixOperacaoStatusPort);
    }

    @Test
    void ownerComPix_retornaStatusPublico() {
        UUID usuarioId = UUID.randomUUID();
        UUID operacaoId = UUID.randomUUID();
        UUID credoraId = UUID.randomUUID();
        UUID contratoId = UUID.randomUUID();
        stubCredoraComOperacao(usuarioId, credoraId, operacaoId, contratoId);
        PixOperacaoStatusView view =
                new PixOperacaoStatusView("LIQUIDADO", new BigDecimal("1500.00"), OffsetDateTime.now());
        when(pixOperacaoStatusPort.consultarPorContrato(contratoId)).thenReturn(Optional.of(view));

        assertThat(useCase.executar(usuarioId, operacaoId)).isSameAs(view);
    }

    @Test
    void usuarioSemCredora_lancaNaoEncontrado_semConsultarOperacaoOuPix() {
        UUID usuarioId = UUID.randomUUID();
        when(empresaRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(usuarioId, UUID.randomUUID()))
                .isInstanceOf(StatusPixOperacaoNaoEncontradoException.class);

        verifyNoInteractions(operacaoRepository);
        verifyNoInteractions(pixOperacaoStatusPort);
    }

    @Test
    void operacaoAlheiaOuInexistente_lancaNaoEncontrado_semConsultarPix() {
        UUID usuarioId = UUID.randomUUID();
        UUID credoraId = UUID.randomUUID();
        EmpresaCredora credora = mock(EmpresaCredora.class);
        when(credora.getId()).thenReturn(credoraId);
        when(empresaRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(credora));
        when(operacaoRepository.findByIdAndEmpresaCredoraId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(usuarioId, UUID.randomUUID()))
                .isInstanceOf(StatusPixOperacaoNaoEncontradoException.class);

        verifyNoInteractions(pixOperacaoStatusPort);
    }

    @Test
    void operacaoSemDesembolsoPix_lancaNaoEncontrado() {
        UUID usuarioId = UUID.randomUUID();
        UUID operacaoId = UUID.randomUUID();
        UUID contratoId = UUID.randomUUID();
        stubCredoraComOperacao(usuarioId, UUID.randomUUID(), operacaoId, contratoId);
        when(pixOperacaoStatusPort.consultarPorContrato(contratoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(usuarioId, operacaoId))
                .isInstanceOf(StatusPixOperacaoNaoEncontradoException.class);
    }

    @Test
    void leituraNaoTemSideEffect() {
        UUID usuarioId = UUID.randomUUID();
        UUID operacaoId = UUID.randomUUID();
        UUID contratoId = UUID.randomUUID();
        stubCredoraComOperacao(usuarioId, UUID.randomUUID(), operacaoId, contratoId);
        when(pixOperacaoStatusPort.consultarPorContrato(contratoId))
                .thenReturn(Optional.of(
                        new PixOperacaoStatusView("LIQUIDADO", new BigDecimal("10.00"), OffsetDateTime.now())));

        useCase.executar(usuarioId, operacaoId);

        verify(empresaRepository, never()).save(any());
        verify(operacaoRepository, never()).save(any());
    }

    private void stubCredoraComOperacao(UUID usuarioId, UUID credoraId, UUID operacaoId, UUID contratoId) {
        EmpresaCredora credora = mock(EmpresaCredora.class);
        when(credora.getId()).thenReturn(credoraId);
        when(empresaRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(credora));
        OperacaoFinanciada operacao = mock(OperacaoFinanciada.class);
        when(operacao.getContratoId()).thenReturn(contratoId);
        when(operacaoRepository.findByIdAndEmpresaCredoraId(operacaoId, credoraId))
                .thenReturn(Optional.of(operacao));
    }
}
