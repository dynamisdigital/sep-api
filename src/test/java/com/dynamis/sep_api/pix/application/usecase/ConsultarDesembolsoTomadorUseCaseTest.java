package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.PixDesembolsoTomadorResult;
import com.dynamis.sep_api.pix.application.port.out.ContratoDesembolsoQueryPort;
import com.dynamis.sep_api.pix.application.port.out.dto.ContratoDesembolsoView;
import com.dynamis.sep_api.pix.domain.exception.PixLeituraNaoEncontradaException;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixPublico;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
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

class ConsultarDesembolsoTomadorUseCaseTest {

    private ContratoDesembolsoQueryPort contratoPort;
    private PixTransferenciaRepository transferenciaRepository;
    private ConsultarDesembolsoTomadorUseCase useCase;

    @BeforeEach
    void setup() {
        contratoPort = mock(ContratoDesembolsoQueryPort.class);
        transferenciaRepository = mock(PixTransferenciaRepository.class);
        useCase = new ConsultarDesembolsoTomadorUseCase(contratoPort, transferenciaRepository);
    }

    @Test
    void ownerComTransferencia_mapeiaStatusPublicoValorEData() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        OffsetDateTime atualizadoEm = OffsetDateTime.now();
        stubContrato(contratoId, tomadorId);
        stubTransferencia(contratoId, StatusPixTransferencia.CONCLUIDA, new BigDecimal("1500.00"), atualizadoEm);

        PixDesembolsoTomadorResult res = useCase.executar(contratoId, tomadorId);

        assertThat(res.status()).isEqualTo(StatusPixPublico.LIQUIDADO);
        assertThat(res.valor()).isEqualByComparingTo("1500.00");
        assertThat(res.atualizadoEm()).isEqualTo(atualizadoEm);
    }

    @Test
    void ownerComTransferenciaFalhou_retornaFalhou() {
        // Garante que o finder sem filtro de status devolve tentativas terminais e o mapper as reflete.
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        stubContrato(contratoId, tomadorId);
        stubTransferencia(contratoId, StatusPixTransferencia.FALHOU, new BigDecimal("900.00"), OffsetDateTime.now());

        assertThat(useCase.executar(contratoId, tomadorId).status()).isEqualTo(StatusPixPublico.FALHOU);
    }

    @Test
    void ownerSemTransferencia_lancaNaoEncontrada() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        stubContrato(contratoId, tomadorId);
        when(transferenciaRepository.findFirstByContratoIdOrderByDataCriacaoDesc(contratoId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(contratoId, tomadorId))
                .isInstanceOf(PixLeituraNaoEncontradaException.class);
    }

    @Test
    void contratoInexistente_lancaNaoEncontrada_semConsultarTransferencia() {
        UUID contratoId = UUID.randomUUID();
        when(contratoPort.buscarPorContrato(contratoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(contratoId, UUID.randomUUID()))
                .isInstanceOf(PixLeituraNaoEncontradaException.class);

        verifyNoInteractions(transferenciaRepository);
    }

    @Test
    void contratoAlheio_lancaNaoEncontrada_semConsultarTransferencia() {
        UUID contratoId = UUID.randomUUID();
        stubContrato(contratoId, UUID.randomUUID());

        assertThatThrownBy(() -> useCase.executar(contratoId, UUID.randomUUID()))
                .isInstanceOf(PixLeituraNaoEncontradaException.class);

        verifyNoInteractions(transferenciaRepository);
    }

    @Test
    void leituraNaoTemSideEffect() {
        UUID contratoId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        stubContrato(contratoId, tomadorId);
        stubTransferencia(contratoId, StatusPixTransferencia.CRIADA, new BigDecimal("100.00"), OffsetDateTime.now());

        useCase.executar(contratoId, tomadorId);

        verify(transferenciaRepository, never()).save(any());
        verify(transferenciaRepository, never()).delete(any());
    }

    private void stubContrato(UUID contratoId, UUID tomadorId) {
        when(contratoPort.buscarPorContrato(contratoId))
                .thenReturn(Optional.of(new ContratoDesembolsoView(
                        contratoId, UUID.randomUUID(), tomadorId, new BigDecimal("1500.00"), true)));
    }

    private void stubTransferencia(
            UUID contratoId, StatusPixTransferencia status, BigDecimal valor, OffsetDateTime atualizadoEm) {
        PixTransferencia transferencia = mock(PixTransferencia.class);
        when(transferencia.getStatus()).thenReturn(status);
        when(transferencia.getValor()).thenReturn(valor);
        when(transferencia.getDataModificacao()).thenReturn(atualizadoEm);
        when(transferenciaRepository.findFirstByContratoIdOrderByDataCriacaoDesc(contratoId))
                .thenReturn(Optional.of(transferencia));
    }
}
