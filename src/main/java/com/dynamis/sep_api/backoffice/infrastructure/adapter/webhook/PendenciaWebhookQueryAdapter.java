package com.dynamis.sep_api.backoffice.infrastructure.adapter.webhook;

import com.dynamis.sep_api.backoffice.application.port.out.PendenciaWebhookQueryPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.WebhookPendenciaView;
import com.dynamis.sep_api.shared.domain.model.WebhookEventStatus;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/** Adapter de fronteira — delega ao repository compartilhado de webhook. */
@Component
public class PendenciaWebhookQueryAdapter implements PendenciaWebhookQueryPort {

    private static final Set<WebhookEventStatus> NAO_PROCESSADOS =
            Set.of(WebhookEventStatus.FALHOU, WebhookEventStatus.PENDENTE);

    private final WebhookEventLogRepository repository;

    public PendenciaWebhookQueryAdapter(WebhookEventLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<WebhookPendenciaView> webhooksNaoProcessados(OffsetDateTime corte) {
        return repository.findByStatusInAndDataModificacaoBefore(NAO_PROCESSADOS, corte).stream()
                .map(w -> new WebhookPendenciaView(
                        w.getId(), w.getProvider(), w.getStatus() == WebhookEventStatus.FALHOU))
                .toList();
    }
}
