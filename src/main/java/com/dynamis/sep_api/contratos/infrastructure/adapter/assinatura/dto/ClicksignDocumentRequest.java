package com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body do endpoint {@code POST /api/v1/documents} da Clicksign (Sprint 11 Task 11.4).
 * Envia o PDF como data URL ({@code data:application/pdf;base64,...}).
 */
public record ClicksignDocumentRequest(@JsonProperty("document") Document document) {

    public record Document(
            @JsonProperty("path") String path,
            @JsonProperty("content_base64") String contentBase64,
            @JsonProperty("deadline_at") String deadlineAt) {}
}
