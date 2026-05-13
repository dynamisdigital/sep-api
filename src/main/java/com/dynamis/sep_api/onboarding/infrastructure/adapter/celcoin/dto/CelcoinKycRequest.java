package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * Payload enviado ao endpoint Celcoin Onboarding KYC PF.
 *
 * <p>Estrutura baseada na documentacao Celcoin (sandbox); evoluir conforme contratos reais. Fica
 * encapsulado no adapter — dominio nao conhece.
 */
public record CelcoinKycRequest(
        @JsonProperty("external_id") String externalId,
        @JsonProperty("document_number") String cpf,
        @JsonProperty("full_name") String nomeCompleto,
        @JsonProperty("birth_date") LocalDate dataNascimento,
        @JsonProperty("documents") List<DocumentoRef> documentos) {

    public record DocumentoRef(@JsonProperty("type") String tipo, @JsonProperty("sha256") String sha256) {}
}
