package com.dynamis.sep_api.credito.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta Celcoin/Finansystech ao criar consentimento Open Finance.
 *
 * @param idConsentimento id externo gerado pelo Celcoin (correlaciona o callback posterior)
 * @param urlAutorizacao URL Open Finance Brasil pra o tomador autorizar (handoff redirect)
 * @param expiracao ISO-8601 com offset; null aceito quando provider nao expor
 */
public record CelcoinOpenFinanceConsentResponse(
        @JsonProperty("consent_id") String idConsentimento,
        @JsonProperty("authorization_url") String urlAutorizacao,
        @JsonProperty("expires_at") String expiracao) {}
