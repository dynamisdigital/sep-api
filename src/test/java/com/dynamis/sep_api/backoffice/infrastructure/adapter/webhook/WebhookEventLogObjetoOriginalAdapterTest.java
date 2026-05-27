package com.dynamis.sep_api.backoffice.infrastructure.adapter.webhook;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.domain.model.WebhookEventStatus;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookEventLogObjetoOriginalAdapterTest {

    @Test
    void tipoSuportado_eWebhookEventLog() {
        assertThat(new WebhookEventLogObjetoOriginalAdapter(mock(WebhookEventLogRepository.class)).tipoSuportado())
                .isEqualTo(TipoEntidadeReferenciada.WEBHOOK_EVENT_LOG);
    }

    @Test
    void buscar_existente_devolveResumo() {
        WebhookEventLogRepository repo = mock(WebhookEventLogRepository.class);
        UUID id = UUID.randomUUID();
        WebhookEventLog w = mock(WebhookEventLog.class);
        when(w.getId()).thenReturn(id);
        when(w.getProvider()).thenReturn("celcoin");
        when(w.getEvent()).thenReturn("kyc.updated");
        when(w.getStatus()).thenReturn(WebhookEventStatus.FALHOU);
        when(repo.findById(id)).thenReturn(Optional.of(w));

        Optional<ObjetoOriginalResumo> resumo = new WebhookEventLogObjetoOriginalAdapter(repo).buscar(id);

        assertThat(resumo).isPresent();
        assertThat(resumo.get().status()).isEqualTo("FALHOU");
        assertThat(resumo.get().descricaoCurta()).contains("celcoin").contains("kyc.updated");
    }
}
