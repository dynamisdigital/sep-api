package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.AporteCredoraView;
import com.dynamis.sep_api.credores.application.dto.ReconciliarAporteCredoraCommand;
import com.dynamis.sep_api.credores.application.port.out.ReconciliarAporteEscrowPort;
import com.dynamis.sep_api.credores.domain.event.AporteCredoraFalhouEvent;
import com.dynamis.sep_api.credores.domain.event.AporteCredoraLiquidadoEvent;
import com.dynamis.sep_api.credores.domain.exception.AporteNaoEncontradoException;
import com.dynamis.sep_api.credores.domain.exception.AporteReconciliacaoConflitanteException;
import com.dynamis.sep_api.credores.domain.model.AporteCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusAporteCredora;
import com.dynamis.sep_api.credores.infrastructure.persistence.AporteCredoraRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes da reconciliacao fake do aporte (Sprint 29 Task 29.5): transicoes terminais, replay
 * idempotente sem auditoria duplicada, conflito pos-terminal rejeitado e 404 neutro.
 */
class ReconciliarAporteCredoraUseCaseTest {

    private static final String REFERENCIA = "3f1c2b4a-0000-0000-0000-000000000001";

    private AporteCredoraRepository aporteRepository;
    private ReconciliarAporteEscrowPort escrowPort;
    private ApplicationEventPublisher eventPublisher;
    private ReconciliarAporteCredoraUseCase useCase;

    private AporteCredora aporte;

    @BeforeEach
    void setup() {
        aporteRepository = mock(AporteCredoraRepository.class);
        escrowPort = mock(ReconciliarAporteEscrowPort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new ReconciliarAporteCredoraUseCase(aporteRepository, escrowPort, eventPublisher);

        aporte = AporteCredora.registrar(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("2500.00"), "idem-key-1");
        aporte.marcarEmProcessamento(REFERENCIA);
        when(aporteRepository.findByReferenciaEscrow(REFERENCIA)).thenReturn(Optional.of(aporte));
    }

    private ReconciliarAporteCredoraCommand liquidado() {
        return new ReconciliarAporteCredoraCommand(REFERENCIA, StatusAporteCredora.LIQUIDADO, null);
    }

    private ReconciliarAporteCredoraCommand falhou(String motivo) {
        return new ReconciliarAporteCredoraCommand(REFERENCIA, StatusAporteCredora.FALHOU, motivo);
    }

    @Test
    void liquidaAporteEmProcessamentoComAuditoriaUnica() {
        AporteCredoraView view = useCase.executar(liquidado());

        assertThat(view.status()).isEqualTo(StatusAporteCredora.LIQUIDADO);
        assertThat(aporte.getStatus()).isEqualTo(StatusAporteCredora.LIQUIDADO);
        verify(escrowPort).liquidar(REFERENCIA);

        ArgumentCaptor<AporteCredoraLiquidadoEvent> captor = ArgumentCaptor.forClass(AporteCredoraLiquidadoEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().aporteId()).isEqualTo(aporte.getId());
        assertThat(captor.getValue().valor()).isEqualByComparingTo("2500.00");
    }

    @Test
    void falhaAporteComMotivoSanitizado() {
        AporteCredoraView view = useCase.executar(falhou("Aporte recusado na liquidacao"));

        assertThat(view.status()).isEqualTo(StatusAporteCredora.FALHOU);
        assertThat(aporte.getMotivoFalhaSanitizado()).isEqualTo("Aporte recusado na liquidacao");
        verify(escrowPort).falhar(REFERENCIA);

        ArgumentCaptor<AporteCredoraFalhouEvent> captor = ArgumentCaptor.forClass(AporteCredoraFalhouEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().motivoSanitizado()).isEqualTo("Aporte recusado na liquidacao");
    }

    @Test
    void replayLiquidadoNaoAlteraNemDuplicaAuditoria() {
        useCase.executar(liquidado());

        AporteCredoraView replay = useCase.executar(liquidado());

        assertThat(replay.status()).isEqualTo(StatusAporteCredora.LIQUIDADO);
        // porta e evento exatamente 1x — replay nao re-executa efeito nem auditoria
        verify(escrowPort).liquidar(REFERENCIA);
        verify(eventPublisher).publishEvent(any(AporteCredoraLiquidadoEvent.class));
    }

    @Test
    void replayFalhouNaoAlteraNemDuplicaAuditoria() {
        useCase.executar(falhou("Aporte recusado"));

        AporteCredoraView replay = useCase.executar(falhou("Aporte recusado"));

        assertThat(replay.status()).isEqualTo(StatusAporteCredora.FALHOU);
        verify(escrowPort).falhar(REFERENCIA);
        verify(eventPublisher).publishEvent(any(AporteCredoraFalhouEvent.class));
    }

    @Test
    void resultadoConflitanteAposTerminalRejeitadoSemAlterarEstado() {
        useCase.executar(liquidado());

        assertThatThrownBy(() -> useCase.executar(falhou("tentativa de falha pos-liquidacao")))
                .isInstanceOf(AporteReconciliacaoConflitanteException.class);
        assertThat(aporte.getStatus()).isEqualTo(StatusAporteCredora.LIQUIDADO);
        verify(escrowPort, never()).falhar(any());

        AporteCredora falhado =
                AporteCredora.registrar(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), "idem-key-2");
        falhado.marcarEmProcessamento("ref-2");
        falhado.marcarFalhou("Aporte recusado");
        when(aporteRepository.findByReferenciaEscrow("ref-2")).thenReturn(Optional.of(falhado));

        assertThatThrownBy(() -> useCase.executar(
                        new ReconciliarAporteCredoraCommand("ref-2", StatusAporteCredora.LIQUIDADO, null)))
                .isInstanceOf(AporteReconciliacaoConflitanteException.class);
        assertThat(falhado.getStatus()).isEqualTo(StatusAporteCredora.FALHOU);
        verify(escrowPort, never()).liquidar("ref-2");
    }

    @Test
    void referenciaDesconhecidaRetorna404NeutroSemEcoarReferencia() {
        when(aporteRepository.findByReferenciaEscrow("ref-fantasma")).thenReturn(Optional.empty());

        Throwable erro = catchThrowable(() -> useCase.executar(
                new ReconciliarAporteCredoraCommand("ref-fantasma", StatusAporteCredora.LIQUIDADO, null)));

        assertThat(erro).isInstanceOf(AporteNaoEncontradoException.class).hasMessageNotContaining("ref-fantasma");
        verifyNoInteractions(escrowPort, eventPublisher);
    }

    @Test
    void validaReferenciaResultadoEMotivo() {
        assertThatThrownBy(() ->
                        useCase.executar(new ReconciliarAporteCredoraCommand(" ", StatusAporteCredora.LIQUIDADO, null)))
                .isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar(new ReconciliarAporteCredoraCommand(REFERENCIA, null, null)))
                .isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar(
                        new ReconciliarAporteCredoraCommand(REFERENCIA, StatusAporteCredora.PENDENTE, null)))
                .isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar(
                        new ReconciliarAporteCredoraCommand(REFERENCIA, StatusAporteCredora.EM_PROCESSAMENTO, null)))
                .isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar(falhou(" "))).isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar(falhou("x".repeat(256)))).isInstanceOf(ValidacaoException.class);
        verifyNoInteractions(escrowPort, eventPublisher);
    }
}
