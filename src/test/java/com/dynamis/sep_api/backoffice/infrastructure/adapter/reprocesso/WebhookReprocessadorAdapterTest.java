package com.dynamis.sep_api.backoffice.infrastructure.adapter.reprocesso;

import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.domain.model.WebhookEventStatus;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookReprocessadorAdapterTest {

    @Test
    void naoEncontrado_devolveFalha() {
        WebhookEventLogRepository repo = mock(WebhookEventLogRepository.class);
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        ResultadoReprocesso r = new WebhookReprocessadorAdapter(repo).reprocessar(id);

        assertThat(r.status()).isEqualTo(StatusReprocesso.FALHA);
        assertThat(r.mensagemTecnica()).contains("nao encontrado");
        verify(repo, never()).save(any());
    }

    @Test
    void jaProcessado_devolveFalhaSemMarcar() {
        WebhookEventLogRepository repo = mock(WebhookEventLogRepository.class);
        UUID id = UUID.randomUUID();
        WebhookEventLog evento = mock(WebhookEventLog.class);
        when(evento.getStatus()).thenReturn(WebhookEventStatus.PROCESSADO);
        when(repo.findById(id)).thenReturn(Optional.of(evento));

        ResultadoReprocesso r = new WebhookReprocessadorAdapter(repo).reprocessar(id);

        assertThat(r.status()).isEqualTo(StatusReprocesso.FALHA);
        verify(evento, never()).marcarProcessado();
        verify(repo, never()).save(any());
    }

    @Test
    void falhouOuPendente_marcaProcessadoEDevolveSucesso() {
        WebhookEventLogRepository repo = mock(WebhookEventLogRepository.class);
        UUID id = UUID.randomUUID();
        WebhookEventLog evento = mock(WebhookEventLog.class);
        when(evento.getId()).thenReturn(id);
        when(evento.getProvider()).thenReturn("celcoin");
        when(evento.getEvent()).thenReturn("kyc.updated");
        when(evento.getStatus()).thenReturn(WebhookEventStatus.FALHOU);
        when(repo.findById(id)).thenReturn(Optional.of(evento));

        ResultadoReprocesso r = new WebhookReprocessadorAdapter(repo).reprocessar(id);

        assertThat(r.status()).isEqualTo(StatusReprocesso.SUCESSO);
        verify(evento).marcarProcessado();
        verify(repo).save(evento);
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
