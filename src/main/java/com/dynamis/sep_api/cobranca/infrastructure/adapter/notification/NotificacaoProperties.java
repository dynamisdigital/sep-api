package com.dynamis.sep_api.cobranca.infrastructure.adapter.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * Binding tipado das properties {@code app.notificacoes.*} (Sprint 13 - ADR 0014).
 *
 * <p>Defaults seguros sao definidos em {@code application.yml}. Validacao de credencial real
 * acontece no construtor de cada adapter quando o provider correspondente esta ativo
 * ({@code provider=smtp-zenvia}).
 */
@ConfigurationProperties(prefix = "app.notificacoes")
public record NotificacaoProperties(String provider, String remetenteEmail, Zenvia zenvia) {

    /** Conjunto fechado — typo na property ({@code smtp_zenvia}, etc.) falha no boot. */
    public static final Set<String> PROVIDERS_VALIDOS = Set.of("log", "smtp-zenvia");

    public NotificacaoProperties {
        if (provider == null || provider.isBlank()) {
            provider = "log";
        }
        if (!PROVIDERS_VALIDOS.contains(provider)) {
            throw new IllegalArgumentException(
                    "app.notificacoes.provider invalido: '" + provider + "' (aceito: " + PROVIDERS_VALIDOS + ")");
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
