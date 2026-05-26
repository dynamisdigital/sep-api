package com.dynamis.sep_api.backoffice.application.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thresholds operacionais do {@link VerificadorPendenciasJob} (Sprint 14 Task 14.2). Defaults
 * acompanham a spec 014; podem ser sobrescritos por env var.
 */
@ConfigurationProperties(prefix = "app.backoffice.verificador")
public record BackofficeVerificadorProperties(
        String cron, int propostaPendenciaHoras, int contratoAceitoHoras, int webhookFalhouHoras) {

    public BackofficeVerificadorProperties {
        if (propostaPendenciaHoras <= 0) {
            throw new IllegalArgumentException("propostaPendenciaHoras deve ser positivo");
        }
        if (contratoAceitoHoras <= 0) {
            throw new IllegalArgumentException("contratoAceitoHoras deve ser positivo");
        }
        if (webhookFalhouHoras <= 0) {
            throw new IllegalArgumentException("webhookFalhouHoras deve ser positivo");
        }
    }
}
