package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Payload enviado ao endpoint Celcoin Onboarding KYB PJ.
 *
 * <p>Estrutura inferida da documentacao Celcoin (sandbox); evoluir conforme contrato real
 * estabilizar. Fica encapsulado no adapter — dominio nao conhece.
 */
public record CelcoinKybRequest(
        @JsonProperty("external_id") String externalId,
        @JsonProperty("tax_id") String cnpj,
        @JsonProperty("legal_name") String razaoSocial,
        @JsonProperty("documents") List<DocumentoRef> documentos) {

    public record DocumentoRef(@JsonProperty("type") String tipo, @JsonProperty("sha256") String sha256) {}
}
