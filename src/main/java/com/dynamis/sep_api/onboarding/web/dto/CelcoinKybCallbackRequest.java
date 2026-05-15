package com.dynamis.sep_api.onboarding.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Payload do webhook Celcoin KYB. {@code external_id} mapeia para o {@code solicitacaoId} usado
 * em {@code POST /companies} (Task 7.3). Schema flexivel (campos desconhecidos ignorados via
 * config global do Jackson).
 */
public record CelcoinKybCallbackRequest(
        @JsonProperty("external_id") String externalId,
        @JsonProperty("registration_status") String situacao,
        @JsonProperty("legal_name") String razaoSocial,
        @JsonProperty("trade_name") String nomeFantasia,
        @JsonProperty("primary_cnae") String cnaePrincipal,
        @JsonProperty("secondary_cnaes") String cnaesSecundarios,
        @JsonProperty("share_capital") BigDecimal capitalSocial,
        @JsonProperty("opening_date") LocalDate dataAbertura,
        @JsonProperty("legal_representatives") List<RepresentanteCallback> representantes) {

    public record RepresentanteCallback(
            @JsonProperty("full_name") String nome,
            @JsonProperty("document_number") String cpf,
            @JsonProperty("role") String cargo) {}
}
