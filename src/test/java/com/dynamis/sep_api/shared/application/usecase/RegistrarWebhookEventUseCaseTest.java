package com.dynamis.sep_api.shared.application.usecase;

import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.domain.model.WebhookEventStatus;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrarWebhookEventUseCaseTest {

    private final WebhookEventLogRepository repository = mock(WebhookEventLogRepository.class);
    private final RegistrarWebhookEventUseCase useCase = new RegistrarWebhookEventUseCase(repository);

    @Test
    void gravaEventoNovoComoPendente() {
        when(repository.existsByIdempotencyKey("k1")).thenReturn(false);

        boolean criado = useCase.executar("celcoin", "pagamento_recebido", "k1", "sig", "{\"a\":1}");

        assertThat(criado).isTrue();
        ArgumentCaptor<WebhookEventLog> captor = ArgumentCaptor.forClass(WebhookEventLog.class);
        verify(repository).save(captor.capture());
        WebhookEventLog saved = captor.getValue();
        assertThat(saved.getProvider()).isEqualTo("celcoin");
        assertThat(saved.getEvent()).isEqualTo("pagamento_recebido");
        assertThat(saved.getIdempotencyKey()).isEqualTo("k1");
        assertThat(saved.getStatus()).isEqualTo(WebhookEventStatus.PENDENTE);
        assertThat(saved.getDataRecebimento()).isNotNull();
    }

    @Test
    void idempotencyDuplicadaNaoGravaENaoFalha() {
        when(repository.existsByIdempotencyKey("k1")).thenReturn(true);

        boolean criado = useCase.executar("celcoin", "pagamento_recebido", "k1", "sig", "{}");

        assertThat(criado).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void corridaConcorrenteRetornaFalseSemPropagar() {
        when(repository.existsByIdempotencyKey("k1")).thenReturn(false);
        when(repository.save(any(WebhookEventLog.class))).thenThrow(new DataIntegrityViolationException("dup"));

        boolean criado = useCase.executar("celcoin", "pagamento_recebido", "k1", "sig", "{}");

        assertThat(criado).isFalse();
    }

    @Test
    void argumentosObrigatoriosVaziosLancam400() {
        assertThatThrownBy(() -> useCase.executar("", "ev", "k", "s", "{}")).isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar("p", "", "k", "s", "{}")).isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar("p", "ev", "", "s", "{}")).isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> useCase.executar("p", "ev", "k", "s", "")).isInstanceOf(ValidacaoException.class);
    }
}
