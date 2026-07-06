package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.PixPagamentoParcelaResult;
import com.dynamis.sep_api.pix.application.port.out.ParcelaTomadorQueryPort;
import com.dynamis.sep_api.pix.domain.exception.PixLeituraNaoEncontradaException;
import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixParcelaPublico;
import com.dynamis.sep_api.pix.domain.vo.StatusPixRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixReferenciaRecebimentoRepository;
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

class ConsultarStatusPixParcelaUseCaseTest {

    private ParcelaTomadorQueryPort parcelaTomadorPort;
    private PixReferenciaRecebimentoRepository referenciaRepository;
    private PixRecebimentoRepository recebimentoRepository;
    private ConsultarStatusPixParcelaUseCase useCase;

    @BeforeEach
    void setup() {
        parcelaTomadorPort = mock(ParcelaTomadorQueryPort.class);
        referenciaRepository = mock(PixReferenciaRecebimentoRepository.class);
        recebimentoRepository = mock(PixRecebimentoRepository.class);
        useCase = new ConsultarStatusPixParcelaUseCase(parcelaTomadorPort, referenciaRepository, recebimentoRepository);
    }

    @Test
    void ownerComReferenciaERecebimento_mapeiaEstadoPublico() {
        UUID parcelaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        UUID referenciaId = UUID.randomUUID();
        when(parcelaTomadorPort.tomadorIdDaParcela(parcelaId)).thenReturn(Optional.of(tomadorId));
        stubReferencia(parcelaId, referenciaId, StatusPixReferenciaRecebimento.ATIVA);
        PixRecebimento recebimento = rec(StatusPixRecebimento.CONCILIADO);
        when(recebimentoRepository.findFirstByReferenciaIdOrderByDataCriacaoDesc(referenciaId))
                .thenReturn(Optional.of(recebimento));

        PixPagamentoParcelaResult r = useCase.executar(parcelaId, tomadorId);

        assertThat(r.status()).isEqualTo(StatusPixParcelaPublico.LIQUIDADO);
    }

    @Test
    void recebimentoBuscadoPelaReferenciaAtual_naoCasaComReferenciaAntiga() {
        // Referencia nova ATIVA sem recebimento proprio: sem recebimento por referenciaId -> AGUARDANDO,
        // mesmo que exista recebimento divergente de uma referencia antiga da parcela.
        UUID parcelaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        UUID referenciaAtualId = UUID.randomUUID();
        when(parcelaTomadorPort.tomadorIdDaParcela(parcelaId)).thenReturn(Optional.of(tomadorId));
        stubReferencia(parcelaId, referenciaAtualId, StatusPixReferenciaRecebimento.ATIVA);
        when(recebimentoRepository.findFirstByReferenciaIdOrderByDataCriacaoDesc(referenciaAtualId))
                .thenReturn(Optional.empty());

        PixPagamentoParcelaResult r = useCase.executar(parcelaId, tomadorId);

        assertThat(r.status()).isEqualTo(StatusPixParcelaPublico.AGUARDANDO);
        verify(recebimentoRepository).findFirstByReferenciaIdOrderByDataCriacaoDesc(referenciaAtualId);
    }

    @Test
    void parcelaInexistente_lancaNaoEncontrada_semConsultarReferencia() {
        UUID parcelaId = UUID.randomUUID();
        when(parcelaTomadorPort.tomadorIdDaParcela(parcelaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(parcelaId, UUID.randomUUID()))
                .isInstanceOf(PixLeituraNaoEncontradaException.class);

        verifyNoInteractions(referenciaRepository);
        verifyNoInteractions(recebimentoRepository);
    }

    @Test
    void parcelaAlheia_lancaNaoEncontrada_semConsultarReferencia() {
        UUID parcelaId = UUID.randomUUID();
        when(parcelaTomadorPort.tomadorIdDaParcela(parcelaId)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> useCase.executar(parcelaId, UUID.randomUUID()))
                .isInstanceOf(PixLeituraNaoEncontradaException.class);

        verifyNoInteractions(referenciaRepository);
        verifyNoInteractions(recebimentoRepository);
    }

    @Test
    void ownerSemReferencia_lancaNaoEncontrada() {
        UUID parcelaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        when(parcelaTomadorPort.tomadorIdDaParcela(parcelaId)).thenReturn(Optional.of(tomadorId));
        when(referenciaRepository.findFirstByParcelaIdOrderByDataCriacaoDesc(parcelaId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(parcelaId, tomadorId))
                .isInstanceOf(PixLeituraNaoEncontradaException.class);

        verifyNoInteractions(recebimentoRepository);
    }

    @Test
    void leituraNaoTemSideEffect() {
        UUID parcelaId = UUID.randomUUID();
        UUID tomadorId = UUID.randomUUID();
        UUID referenciaId = UUID.randomUUID();
        when(parcelaTomadorPort.tomadorIdDaParcela(parcelaId)).thenReturn(Optional.of(tomadorId));
        stubReferencia(parcelaId, referenciaId, StatusPixReferenciaRecebimento.ATIVA);
        when(recebimentoRepository.findFirstByReferenciaIdOrderByDataCriacaoDesc(referenciaId))
                .thenReturn(Optional.empty());

        useCase.executar(parcelaId, tomadorId);

        verify(referenciaRepository, never()).save(any());
        verify(recebimentoRepository, never()).save(any());
    }

    private void stubReferencia(UUID parcelaId, UUID referenciaId, StatusPixReferenciaRecebimento status) {
        PixReferenciaRecebimento referencia = mock(PixReferenciaRecebimento.class);
        when(referencia.getId()).thenReturn(referenciaId);
        when(referencia.getStatus()).thenReturn(status);
        when(referencia.getValorEsperado()).thenReturn(new BigDecimal("350.00"));
        when(referencia.getDataModificacao()).thenReturn(OffsetDateTime.parse("2026-06-01T10:00:00Z"));
        when(referenciaRepository.findFirstByParcelaIdOrderByDataCriacaoDesc(parcelaId))
                .thenReturn(Optional.of(referencia));
    }

    private static PixRecebimento rec(StatusPixRecebimento status) {
        PixRecebimento recebimento = mock(PixRecebimento.class);
        when(recebimento.getStatus()).thenReturn(status);
        when(recebimento.getDataModificacao()).thenReturn(OffsetDateTime.parse("2026-06-02T10:00:00Z"));
        return recebimento;
    }
}
