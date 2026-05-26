package com.dynamis.sep_api.backoffice.application.usecase;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Parametros do dashboard de visao consolidada (Sprint 14 Task 14.5). Timezone configura o
 * recorte do "dia operacional" de recebimentos; demais campos sao thresholds operacionais que o
 * step 014.5.3 prescreve como configuraveis (fix review manual Task 14.5).
 */
@ConfigurationProperties(prefix = "app.backoffice.dashboard")
public record BackofficeDashboardProperties(
        String timezone, int tempoMedioJanelaDias, int criticosThresholdHoras, int topTiposLimit) {

    public BackofficeDashboardProperties {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("app.backoffice.dashboard.timezone obrigatorio");
        }
        ZoneId.of(timezone);
        if (tempoMedioJanelaDias <= 0) {
            throw new IllegalArgumentException("tempoMedioJanelaDias deve ser positivo");
        }
        if (criticosThresholdHoras <= 0) {
            throw new IllegalArgumentException("criticosThresholdHoras deve ser positivo");
        }
        if (topTiposLimit <= 0) {
            throw new IllegalArgumentException("topTiposLimit deve ser positivo");
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
