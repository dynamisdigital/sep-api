package com.dynamis.sep_api.backoffice.infrastructure.adapter.reprocesso;

import com.dynamis.sep_api.backoffice.application.port.out.WebhookReprocessadorPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.domain.model.WebhookEventStatus;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter de fronteira (Sprint 14 Task 14.4) que re-dispara processamento de evento em
 * {@code webhook_event_log} (Sprint 4).
 *
 * <p>Comportamento atual:
 *
 * <ul>
 *   <li>Webhook nao encontrado -&gt; {@code FALHA}.
 *   <li>Status {@code PROCESSADO} -&gt; {@code FALHA} (idempotencia — operador nao pode duplicar
 *       processamento ja confirmado).
 *   <li>Status {@code PENDENTE} ou {@code FALHOU} -&gt; marca {@code PROCESSADO} e devolve
 *       {@code SUCESSO}. Pendencia operacional fica resolvida no log; handlers de processamento
 *       real por provider/event serao acoplados em sprints futuras (Epic 15 Pix, Epic 13 KYC
 *       async, etc).
 * </ul>
 */
@Component
public class WebhookReprocessadorAdapter implements WebhookReprocessadorPort {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookReprocessadorAdapter.class);

    private final WebhookEventLogRepository repository;

    public WebhookReprocessadorAdapter(WebhookEventLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ResultadoReprocesso reprocessar(UUID webhookEventId) {
        Optional<WebhookEventLog> opt = repository.findById(webhookEventId);
        if (opt.isEmpty()) {
            return ResultadoReprocesso.falha("Webhook nao encontrado: " + webhookEventId);
        }
        WebhookEventLog evento = opt.get();
        if (evento.getStatus() == WebhookEventStatus.PROCESSADO) {
            return ResultadoReprocesso.falha("Webhook ja processado: " + webhookEventId);
        }
        evento.marcarProcessado();
        repository.save(evento);
        LOG.info(
                "webhook reprocessado id={} provider={} event={}",
                evento.getId(),
                evento.getProvider(),
                evento.getEvent());
        return ResultadoReprocesso.sucesso("Webhook " + evento.getProvider() + "/" + evento.getEvent() + " reprocessado");
    }
}
