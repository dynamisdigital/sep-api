package com.dynamis.sep_api.credito.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload do webhook Celcoin Open Finance (Sprint 9 Task 9.5). Schema flexivel
 * ({@code fail-on-unknown-properties: false}) acomoda evolucao do provider.
 *
 * @param tipo discriminador do tipo de callback. Aceitos:
 *     {@code consent.authorized}, {@code consent.denied}, {@code consent.expired},
 *     {@code movement.data}. Outros valores -> 202 com WARN (no-op).
 * @param consentId id externo do consentimento — chave de lookup em {@code
 *     consentimento_open_finance.id_externo_celcoin}.
 * @param motivo motivo curto pra denial/expiration; pode ser null.
 */
public record CelcoinOpenFinanceCallbackRequest(
        @JsonProperty("type") String tipo,
        @JsonProperty("consent_id") String consentId,
        @JsonProperty("reason") String motivo) {

    public static final String TIPO_AUTORIZADO = "consent.authorized";
    public static final String TIPO_NEGADO = "consent.denied";
    public static final String TIPO_EXPIRADO = "consent.expired";
    public static final String TIPO_MOVIMENTACAO = "movement.data";

    public boolean isAutorizacao() {
        return TIPO_AUTORIZADO.equalsIgnoreCase(tipo);
    }

    public boolean isNegacao() {
        return TIPO_NEGADO.equalsIgnoreCase(tipo) || TIPO_EXPIRADO.equalsIgnoreCase(tipo);
    }

    public boolean isMovimentacao() {
        return TIPO_MOVIMENTACAO.equalsIgnoreCase(tipo);
    }

    public boolean isTipoConhecido() {
        return isAutorizacao() || isNegacao() || isMovimentacao();
    }
}
