package com.dynamis.sep_api.backoffice.infrastructure.adapter.webhook;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.application.port.out.ObjetoOriginalQueryPort;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Strategy: resolve {@code WEBHOOK_EVENT_LOG} para {@link ObjetoOriginalResumo}. */
@Component
public class WebhookEventLogObjetoOriginalAdapter implements ObjetoOriginalQueryPort {

    private final WebhookEventLogRepository repository;

    public WebhookEventLogObjetoOriginalAdapter(WebhookEventLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public TipoEntidadeReferenciada tipoSuportado() {
        return TipoEntidadeReferenciada.WEBHOOK_EVENT_LOG;
    }

    @Override
    public Optional<ObjetoOriginalResumo> buscar(UUID entidadeId) {
        return repository.findById(entidadeId)
                .map(w -> new ObjetoOriginalResumo(
                        TipoEntidadeReferenciada.WEBHOOK_EVENT_LOG,
                        w.getId(),
                        w.getStatus().name(),
                        "Webhook " + w.getProvider() + " evento " + w.getEvent()));
    }
}
