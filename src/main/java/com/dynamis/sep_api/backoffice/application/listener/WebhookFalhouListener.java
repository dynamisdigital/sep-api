package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.job.BackofficeVerificadorProperties;
import com.dynamis.sep_api.backoffice.application.port.out.PendenciaWebhookQueryPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.WebhookPendenciaView;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Job-driven: cria item {@code WEBHOOK_FALHOU} para entradas da Outbox em {@code FALHOU} ou
 * paradas em {@code PENDENTE} alem do threshold (default 1h). Consumers da Outbox podem ainda
 * nao estar plenamente automatizados na Fase 2, entao itens em {@code PENDENTE} antigo merecem
 * atencao operacional (Sprint 14 Task 14.2).
 *
 * <p>Acesso ao log compartilhado via {@link PendenciaWebhookQueryPort} (port/adapter) — sem
 * importar {@code WebhookEventStatus} do {@code shared} aqui.
 */
@Component
public class WebhookFalhouListener {

    private final PendenciaWebhookQueryPort webhookQuery;
    private final CriarItemFilaOperacionalService criarItem;
    private final BackofficeVerificadorProperties properties;
    private final Clock clock;

    public WebhookFalhouListener(
            PendenciaWebhookQueryPort webhookQuery,
            CriarItemFilaOperacionalService criarItem,
            BackofficeVerificadorProperties properties,
            Clock clock) {
        this.webhookQuery = webhookQuery;
        this.criarItem = criarItem;
        this.properties = properties;
        this.clock = clock;
    }

    public void verificar() {
        OffsetDateTime corte = OffsetDateTime.now(clock).minusHours(properties.webhookFalhouHoras());
        List<WebhookPendenciaView> pendentes = webhookQuery.webhooksNaoProcessados(corte);

        for (WebhookPendenciaView w : pendentes) {
            PrioridadeItem prioridade = w.falhou() ? PrioridadeItem.ALTA : PrioridadeItem.MEDIA;
            String estado = w.falhou() ? "falhou" : "pendente";
            criarItem.criarSeAusente(new CriarItemCommand(
                    TipoItemFila.WEBHOOK_FALHOU,
                    prioridade,
                    TipoEntidadeReferenciada.WEBHOOK_EVENT_LOG,
                    w.webhookEventId(),
                    "Webhook " + w.provider() + " " + estado,
                    "Sem processamento ha mais de " + properties.webhookFalhouHoras() + "h"));
        }
    }
}
