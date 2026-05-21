package com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Request do endpoint {@code POST /api/v1/lists} (Clicksign) — vincula signatario ao documento. */
public record ClicksignSignerRequest(@JsonProperty("list") List list) {

    public record List(
            @JsonProperty("document_key") String documentKey,
            @JsonProperty("signer_email") String signerEmail,
            @JsonProperty("signer_name") String signerName,
            @JsonProperty("sign_as") String signAs) {}
}
