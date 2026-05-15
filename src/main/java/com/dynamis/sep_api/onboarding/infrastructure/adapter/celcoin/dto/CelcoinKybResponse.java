package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Payload retornado pelo endpoint Celcoin Onboarding KYB PJ. Contem dados cadastrais consolidados
 * + lista de representantes legais.
 */
public record CelcoinKybResponse(
        @JsonProperty("registration_status") String situacao,
        @JsonProperty("legal_name") String razaoSocial,
        @JsonProperty("trade_name") String nomeFantasia,
        @JsonProperty("primary_cnae") String cnaePrincipal,
        @JsonProperty("secondary_cnaes") String cnaesSecundarios,
        @JsonProperty("share_capital") BigDecimal capitalSocial,
        @JsonProperty("opening_date") LocalDate dataAbertura,
        @JsonProperty("legal_representatives") List<RepresentanteLegalCelcoin> representantes) {

    public record RepresentanteLegalCelcoin(
            @JsonProperty("full_name") String nome,
            @JsonProperty("document_number") String cpf,
            @JsonProperty("role") String cargo) {}
}
