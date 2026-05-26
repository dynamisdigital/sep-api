package com.dynamis.sep_api.backoffice.application.usecase;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Parametros do dashboard de visao consolidada (Sprint 14 Task 14.5). Timezone configura o
 * recorte de "dia operacional" usado pra somar recebimentos.
 */
@ConfigurationProperties(prefix = "app.backoffice.dashboard")
public record BackofficeDashboardProperties(String timezone) {

    public BackofficeDashboardProperties {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("app.backoffice.dashboard.timezone obrigatorio");
        }
        ZoneId.of(timezone);
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
