package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.job.BackofficeVerificadorProperties;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.domain.model.WebhookEventStatus;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Job-driven: cria item {@code WEBHOOK_FALHOU} para entradas da Outbox em {@code FALHOU} ou
 * paradas em {@code PENDENTE} alem do threshold (default 1h). Consumers da Outbox podem ainda
 * nao estar plenamente automatizados na Fase 2, entao itens em {@code PENDENTE} antigo merecem
 * atencao operacional (Sprint 14 Task 14.2).
 */
@Component
public class WebhookFalhouListener {

    private static final Set<WebhookEventStatus> NAO_PROCESSADOS =
            Set.of(WebhookEventStatus.FALHOU, WebhookEventStatus.PENDENTE);

    private final WebhookEventLogRepository webhookRepository;
    private final CriarItemFilaOperacionalService criarItem;
    private final BackofficeVerificadorProperties properties;
    private final Clock clock;

    public WebhookFalhouListener(
            WebhookEventLogRepository webhookRepository,
            CriarItemFilaOperacionalService criarItem,
            BackofficeVerificadorProperties properties,
            Clock clock) {
        this.webhookRepository = webhookRepository;
        this.criarItem = criarItem;
        this.properties = properties;
        this.clock = clock;
    }

    public void verificar() {
        OffsetDateTime corte = OffsetDateTime.now(clock).minusHours(properties.webhookFalhouHoras());
        List<WebhookEventLog> nao = webhookRepository.findByStatusInAndDataModificacaoBefore(NAO_PROCESSADOS, corte);

        for (WebhookEventLog w : nao) {
            PrioridadeItem prioridade =
                    w.getStatus() == WebhookEventStatus.FALHOU ? PrioridadeItem.ALTA : PrioridadeItem.MEDIA;
            criarItem.criarSeAusente(new CriarItemCommand(
                    TipoItemFila.WEBHOOK_FALHOU,
                    prioridade,
                    TipoEntidadeReferenciada.WEBHOOK_EVENT_LOG,
                    w.getId(),
                    "Webhook " + w.getProvider() + " " + w.getStatus().name().toLowerCase(),
                    "Sem processamento ha mais de " + properties.webhookFalhouHoras() + "h"));
        }
    }
}
