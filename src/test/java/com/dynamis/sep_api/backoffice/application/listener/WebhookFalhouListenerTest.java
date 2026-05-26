package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.job.BackofficeVerificadorProperties;
import com.dynamis.sep_api.backoffice.application.port.out.PendenciaWebhookQueryPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.WebhookPendenciaView;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookFalhouListenerTest {

    @Test
    void falhou_geraPrioridadeAlta_pendente_media() {
        PendenciaWebhookQueryPort port = mock(PendenciaWebhookQueryPort.class);
        CriarItemFilaOperacionalService criarItem = mock(CriarItemFilaOperacionalService.class);
        BackofficeVerificadorProperties props = new BackofficeVerificadorProperties("0 */15 * * * *", 24, 48, 1);
        Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);

        WebhookPendenciaView falhou = new WebhookPendenciaView(UUID.randomUUID(), "celcoin", true);
        WebhookPendenciaView pendente = new WebhookPendenciaView(UUID.randomUUID(), "clicksign", false);
        when(port.webhooksNaoProcessados(any())).thenReturn(List.of(falhou, pendente));
        when(criarItem.criarSeAusente(any())).thenReturn(Optional.of(UUID.randomUUID()));

        new WebhookFalhouListener(port, criarItem, props, clock).verificar();

        ArgumentCaptor<CriarItemCommand> captor = ArgumentCaptor.forClass(CriarItemCommand.class);
        verify(criarItem, times(2)).criarSeAusente(captor.capture());
        List<CriarItemCommand> cmds = captor.getAllValues();
        assertThat(cmds.get(0).tipo()).isEqualTo(TipoItemFila.WEBHOOK_FALHOU);
        assertThat(cmds.get(0).prioridade()).isEqualTo(PrioridadeItem.ALTA);
        assertThat(cmds.get(1).prioridade()).isEqualTo(PrioridadeItem.MEDIA);
    }
}
