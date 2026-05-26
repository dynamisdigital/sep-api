package com.dynamis.sep_api.backoffice.infrastructure.adapter.webhook;

import com.dynamis.sep_api.backoffice.application.port.out.dto.WebhookPendenciaView;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.domain.model.WebhookEventStatus;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PendenciaWebhookQueryAdapterTest {

    @Test
    void mapeiaStatusParaFlagFalhou() {
        WebhookEventLogRepository repo = mock(WebhookEventLogRepository.class);
        WebhookEventLog falhou = mock(WebhookEventLog.class);
        when(falhou.getId()).thenReturn(UUID.randomUUID());
        when(falhou.getProvider()).thenReturn("celcoin");
        when(falhou.getStatus()).thenReturn(WebhookEventStatus.FALHOU);
        WebhookEventLog pendente = mock(WebhookEventLog.class);
        when(pendente.getId()).thenReturn(UUID.randomUUID());
        when(pendente.getProvider()).thenReturn("clicksign");
        when(pendente.getStatus()).thenReturn(WebhookEventStatus.PENDENTE);
        when(repo.findByStatusInAndDataModificacaoBefore(any(), any())).thenReturn(List.of(falhou, pendente));

        List<WebhookPendenciaView> result =
                new PendenciaWebhookQueryAdapter(repo).webhooksNaoProcessados(OffsetDateTime.now());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).falhou()).isTrue();
        assertThat(result.get(0).provider()).isEqualTo("celcoin");
        assertThat(result.get(1).falhou()).isFalse();
        assertThat(result.get(1).provider()).isEqualTo("clicksign");
    }
}
