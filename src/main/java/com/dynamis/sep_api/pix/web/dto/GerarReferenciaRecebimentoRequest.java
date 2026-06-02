package com.dynamis.sep_api.pix.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Payload para gerar uma referencia Pix de recebimento de uma parcela (Sprint 21 Task 21.6). */
@Schema(description = "Solicitacao de geracao de referencia Pix de recebimento")
public record GerarReferenciaRecebimentoRequest(
        @NotNull @Schema(example = "018f3a2b-0000-7000-8000-000000000000") UUID parcelaId) {}
