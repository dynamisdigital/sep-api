package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.EventoWebhookPixNormalizado;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.StatusTransferenciaPixProvider;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderException;
import com.dynamis.sep_api.pix.application.service.SincronizadorStatusTransferencia;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.model.PixWebhookEvent;
import com.dynamis.sep_api.pix.domain.vo.StatusPixWebhookEvent;
import com.dynamis.sep_api.pix.domain.vo.TipoPixWebhookEvent;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

class ProcessarWebhookPixStatusTransferenciaTest {

    private PixProvider pixProvider;
    private PixWebhookEventRepository webhookEventRepository;
    private PixTransferenciaRepository transferenciaRepository;
    private SincronizadorStatusTransferencia sincronizador;
    private ProcessarWebhookPixUseCase useCase;

    private PixWebhookEvent eventoPersistido;

    @BeforeEach
    void setUp() {
        pixProvider = mock(PixProvider.class);
        webhookEventRepository = mock(PixWebhookEventRepository.class);
        PixRecebimentoRepository recebimentoRepository = mock(PixRecebimentoRepository.class);
        transferenciaRepository = mock(PixTransferenciaRepository.class);
        sincronizador = mock(SincronizadorStatusTransferencia.class);
        useCase = new ProcessarWebhookPixUseCase(
                pixProvider,
                webhookEventRepository,
                recebimentoRepository,
                transferenciaRepository,
                sincronizador,
                mock(ApplicationEventPublisher.class));

        when(webhookEventRepository.existsByProviderAndEventId(any(), any())).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(inv -> {
            eventoPersistido = inv.getArgument(0);
            return eventoPersistido;
        });
    }

    private void stubWebhookStatus(String externalId) {
        EventoWebhookPixNormalizado evt = new EventoWebhookPixNormalizado(
                TipoPixWebhookEvent.STATUS_TRANSFERENCIA, "evt-1", null, null, externalId, "a".repeat(64));
        when(pixProvider.normalizarWebhook(any())).thenReturn(evt);
    }

    private PixTransferencia desembolso() {
        PixTransferencia t = PixTransferencia.criarDesembolso(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10000.00"),
                "h".repeat(64),
                "us****om",
                "idem-1",
                "corr-1");
        t.marcarSolicitada("ext-1");
        return t;
    }

    @Test
    void externalIdConhecido_reconsultaProviderESincroniza() {
        stubWebhookStatus("ext-1");
        PixTransferencia t = desembolso();
        when(transferenciaRepository.findByExternalId("ext-1")).thenReturn(Optional.of(t));
        when(pixProvider.consultarTransferencia("ext-1", "corr-1"))
                .thenReturn(new RespostaTransferenciaPix("ext-1", StatusTransferenciaPixProvider.CONCLUIDA));

        ProcessarWebhookPixUseCase.Resultado res = useCase.executar("{}", "corr-1");

        assertThat(res.aceito()).isTrue();
        verify(sincronizador).sincronizar(t, StatusTransferenciaPixProvider.CONCLUIDA);
        verify(transferenciaRepository).save(t);
        assertThat(eventoPersistido.getStatus()).isEqualTo(StatusPixWebhookEvent.PROCESSADO);
    }

    @Test
    void providerFalhaAoReconsultar_marcaWebhookFalhouSem500() {
        stubWebhookStatus("ext-1");
        when(transferenciaRepository.findByExternalId("ext-1")).thenReturn(Optional.of(desembolso()));
        when(pixProvider.consultarTransferencia(any(), any())).thenThrow(new PixProviderException("timeout"));

        ProcessarWebhookPixUseCase.Resultado res = useCase.executar("{}", "corr-1");

        assertThat(res.aceito()).isTrue();
        assertThat(eventoPersistido.getStatus()).isEqualTo(StatusPixWebhookEvent.FALHOU);
    }

    @Test
    void externalIdDesconhecido_marcaIgnoradoSemProvider() {
        stubWebhookStatus("ext-desconhecido");
        when(transferenciaRepository.findByExternalId("ext-desconhecido")).thenReturn(Optional.empty());

        ProcessarWebhookPixUseCase.Resultado res = useCase.executar("{}", "corr-1");

        assertThat(res.aceito()).isTrue();
        verify(pixProvider, never()).consultarTransferencia(any(), any());
        verify(sincronizador, never()).sincronizar(any(), any());
        assertThat(eventoPersistido.getStatus()).isEqualTo(StatusPixWebhookEvent.IGNORADO);
    }
}
