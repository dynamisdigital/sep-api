package com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response do endpoint {@code POST /api/v1/documents} e {@code GET /api/v1/documents/{key}} da
 * Clicksign. {@code status} e o vocabulario nativo (mapeado pra {@code StatusEnvelope} no mapper).
 *
 * <p>{@code downloads.signed_file_url} aparece apenas quando o documento ja foi assinado.
 */
public record ClicksignDocumentResponse(@JsonProperty("document") Document document) {

    public record Document(
            @JsonProperty("key") String key,
            @JsonProperty("status") String status,
            @JsonProperty("updated_at") String updatedAt,
            @JsonProperty("downloads") Downloads downloads) {}

    public record Downloads(@JsonProperty("signed_file_url") String signedFileUrl) {}
}
