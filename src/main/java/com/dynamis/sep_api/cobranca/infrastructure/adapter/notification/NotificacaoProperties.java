package com.dynamis.sep_api.cobranca.infrastructure.adapter.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binding tipado das properties {@code app.notificacoes.*} (Sprint 13 - ADR 0014).
 *
 * <p>Defaults seguros sao definidos em {@code application.yml}. Validacao de credencial real
 * acontece no construtor de cada adapter quando o provider correspondente esta ativo
 * ({@code provider=smtp-zenvia}).
 */
@ConfigurationProperties(prefix = "app.notificacoes")
public record NotificacaoProperties(String provider, String remetenteEmail, Zenvia zenvia) {

    public NotificacaoProperties {
        if (provider == null || provider.isBlank()) {
            provider = "log";
        }
        if (remetenteEmail == null || remetenteEmail.isBlank()) {
            remetenteEmail = "no-reply@sep.local";
        }
        if (zenvia == null) {
            zenvia = new Zenvia(null, null, "SEP", 5000);
        }
    }

    public record Zenvia(String baseUrl, String apiToken, String from, int timeoutMs) {
        public Zenvia {
            if (from == null || from.isBlank()) {
                from = "SEP";
            }
            if (timeoutMs <= 0) {
                timeoutMs = 5000;
            }
        }
    }
}
