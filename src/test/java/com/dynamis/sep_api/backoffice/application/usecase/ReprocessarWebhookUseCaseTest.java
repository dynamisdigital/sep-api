package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.application.port.out.WebhookReprocessadorPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.application.service.AntiAbusoReprocessoService;
import com.dynamis.sep_api.backoffice.domain.event.ReprocessoDisparadoEvent;
import com.dynamis.sep_api.backoffice.domain.exception.LimiteReprocessoExcedidoException;
import com.dynamis.sep_api.backoffice.domain.model.Reprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ReprocessoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReprocessarWebhookUseCaseTest {

    private ReprocessoRepository repo;
    private WebhookReprocessadorPort webhook;
    private AntiAbusoReprocessoService antiAbuso;
    private ApplicationEventPublisher publisher;
    private ReprocessarWebhookUseCase useCase;

    @BeforeEach
    void setup() {
        repo = mock(ReprocessoRepository.class);
        webhook = mock(WebhookReprocessadorPort.class);
        antiAbuso = mock(AntiAbusoReprocessoService.class);
        publisher = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
        useCase = new ReprocessarWebhookUseCase(repo, webhook, antiAbuso, publisher, clock);
        when(repo.save(any(Reprocesso.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void happy_persisteEPublica() {
        UUID webhookId = UUID.randomUUID();
        when(webhook.reprocessar(webhookId)).thenReturn(ResultadoReprocesso.sucesso("HTTP 200"));

        Reprocesso r = useCase.executar(webhookId, UUID.randomUUID(), null);

        assertThat(r.getStatus()).isEqualTo(StatusReprocesso.SUCESSO);
        assertThat(r.getResultado()).isEqualTo("HTTP 200");
        verify(publisher).publishEvent(any(ReprocessoDisparadoEvent.class));
    }

    @Test
    void antiAbusoLanca_naoDispara() {
        UUID webhookId = UUID.randomUUID();
        doThrow(new LimiteReprocessoExcedidoException(webhookId.toString()))
                .when(antiAbuso)
                .validarLimite(any());

        assertThatExceptionOfType(LimiteReprocessoExcedidoException.class)
                .isThrownBy(() -> useCase.executar(webhookId, UUID.randomUUID(), null));
        verify(repo, never()).save(any(Reprocesso.class));
        verify(webhook, never()).reprocessar(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void falhaNoAdapter_persisteComFalha() {
        UUID webhookId = UUID.randomUUID();
        when(webhook.reprocessar(webhookId)).thenReturn(ResultadoReprocesso.falha("HTTP 500"));

        Reprocesso r = useCase.executar(webhookId, UUID.randomUUID(), null);

        assertThat(r.getStatus()).isEqualTo(StatusReprocesso.FALHA);
        assertThat(r.getResultado()).isEqualTo("HTTP 500");
        verify(publisher).publishEvent(any(ReprocessoDisparadoEvent.class));
    }
}
