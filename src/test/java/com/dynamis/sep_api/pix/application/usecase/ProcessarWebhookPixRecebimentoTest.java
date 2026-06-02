package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.EventoWebhookPixNormalizado;
import com.dynamis.sep_api.pix.application.service.SincronizadorStatusTransferencia;
import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixWebhookEvent;
import com.dynamis.sep_api.pix.domain.vo.StatusPixRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixWebhookEvent;
import com.dynamis.sep_api.pix.domain.vo.TipoPixWebhookEvent;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixReferenciaRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Correlacao deterministica do webhook {@code RECEBIMENTO_PIX} com a referencia/parcela (Sprint 21
 * Task 21.3). Sem referencia, o recebimento nao baixa parcela automaticamente — vira {@code
 * NAO_IDENTIFICADO}; com referencia, vira {@code EM_PROCESSAMENTO} (baixa fica para a Task 21.4).
 */
class ProcessarWebhookPixRecebimentoTest {

    private PixProvider pixProvider;
    private PixWebhookEventRepository webhookEventRepository;
    private PixRecebimentoRepository recebimentoRepository;
    private PixReferenciaRecebimentoRepository referenciaRepository;
    private ProcessarWebhookPixUseCase useCase;

    private PixWebhookEvent eventoPersistido;

    @BeforeEach
    void setUp() {
        pixProvider = mock(PixProvider.class);
        webhookEventRepository = mock(PixWebhookEventRepository.class);
        recebimentoRepository = mock(PixRecebimentoRepository.class);
        referenciaRepository = mock(PixReferenciaRecebimentoRepository.class);
        PixTransferenciaRepository transferenciaRepository = mock(PixTransferenciaRepository.class);
        SincronizadorStatusTransferencia sincronizador = mock(SincronizadorStatusTransferencia.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new ProcessarWebhookPixUseCase(
                pixProvider,
                webhookEventRepository,
                recebimentoRepository,
                referenciaRepository,
                transferenciaRepository,
                sincronizador,
                eventPublisher);

        when(webhookEventRepository.existsByProviderAndEventId(any(), any())).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(inv -> {
            eventoPersistido = inv.getArgument(0);
            return eventoPersistido;
        });
        when(recebimentoRepository.findByEndToEndId(any())).thenReturn(Optional.empty());
    }

    private void stubRecebimento(String endToEndId, String txid, String providerReferenciaId) {
        EventoWebhookPixNormalizado evt = new EventoWebhookPixNormalizado(
                TipoPixWebhookEvent.RECEBIMENTO_PIX,
                "evt-1",
                endToEndId,
                new BigDecimal("250.00"),
                null,
                txid,
                providerReferenciaId,
                "a".repeat(64));
        when(pixProvider.normalizarWebhook(any())).thenReturn(evt);
    }

    private PixReferenciaRecebimento referencia(String txid) {
        return PixReferenciaRecebimento.criar(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("250.00"), txid, "corr-1");
    }

    private PixRecebimento capturarRecebimentoSalvo() {
        ArgumentCaptor<PixRecebimento> captor = ArgumentCaptor.forClass(PixRecebimento.class);
        verify(recebimentoRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void referenciaConhecidaPorTxid_recebimentoEmProcessamento() {
        stubRecebimento("E2E-1", "txid-1", null);
        when(referenciaRepository.findByTxid("txid-1")).thenReturn(Optional.of(referencia("txid-1")));

        ProcessarWebhookPixUseCase.Resultado res = useCase.executar("{}", "corr-1");

        assertThat(res.aceito()).isTrue();
        assertThat(capturarRecebimentoSalvo().getStatus()).isEqualTo(StatusPixRecebimento.EM_PROCESSAMENTO);
        assertThat(eventoPersistido.getStatus()).isEqualTo(StatusPixWebhookEvent.PROCESSADO);
    }

    @Test
    void referenciaConhecidaPorProviderRef_quandoTxidAusente() {
        stubRecebimento("E2E-1", null, "prov-ref-9");
        when(referenciaRepository.findByProviderReferenciaId("prov-ref-9"))
                .thenReturn(Optional.of(referencia("txid-9")));

        useCase.executar("{}", "corr-1");

        assertThat(capturarRecebimentoSalvo().getStatus()).isEqualTo(StatusPixRecebimento.EM_PROCESSAMENTO);
    }

    @Test
    void txidDesconhecido_recebimentoNaoIdentificadoSemBaixa() {
        stubRecebimento("E2E-1", "txid-desconhecido", null);
        when(referenciaRepository.findByTxid("txid-desconhecido")).thenReturn(Optional.empty());

        useCase.executar("{}", "corr-1");

        assertThat(capturarRecebimentoSalvo().getStatus()).isEqualTo(StatusPixRecebimento.NAO_IDENTIFICADO);
    }

    @Test
    void semCampoDeCorrelacao_recebimentoNaoIdentificado() {
        stubRecebimento("E2E-1", null, null);

        useCase.executar("{}", "corr-1");

        assertThat(capturarRecebimentoSalvo().getStatus()).isEqualTo(StatusPixRecebimento.NAO_IDENTIFICADO);
        verify(referenciaRepository, never()).findByTxid(any());
        verify(referenciaRepository, never()).findByProviderReferenciaId(any());
    }

    @Test
    void endToEndIdJaRegistrado_idempotenteNaoSalvaNovoRecebimento() {
        stubRecebimento("E2E-1", "txid-1", null);
        when(recebimentoRepository.findByEndToEndId("E2E-1"))
                .thenReturn(Optional.of(PixRecebimento.registrar(
                        "E2E-1", new BigDecimal("250.00"), java.time.OffsetDateTime.now(), "c")));

        useCase.executar("{}", "corr-1");

        verify(recebimentoRepository, never()).save(any());
        verify(referenciaRepository, never()).findByTxid(any());
        assertThat(eventoPersistido.getStatus()).isEqualTo(StatusPixWebhookEvent.PROCESSADO);
    }
}
