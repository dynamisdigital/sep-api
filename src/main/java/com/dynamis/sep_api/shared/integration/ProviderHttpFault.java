package com.dynamis.sep_api.shared.integration;

/**
 * Marcador das excecoes HTTP traduzidas pelos adapters de provider externo (Sprint 32 Task 32.4).
 * Permite ao {@link ProviderRetryConfig} decidir retry por classe de status sem depender dos
 * modulos: 5xx e transiente (reentra); 4xx e erro de contrato/negocio (nao reentra).
 */
public interface ProviderHttpFault {

    int getStatusCode();

    default boolean isServerError() {
        return getStatusCode() >= 500 && getStatusCode() < 600;
    }
}
