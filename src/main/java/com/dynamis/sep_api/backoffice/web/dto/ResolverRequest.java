package com.dynamis.sep_api.backoffice.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Justificativa minima de 20 caracteres pra resolver o item.")
public record ResolverRequest(
        @Schema(example = "Documento recebido e validado manualmente apos contato com tomador")
                @NotBlank
                @Size(min = 20, max = 10000)
                String justificativa) {}
