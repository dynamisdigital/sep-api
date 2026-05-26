package com.dynamis.sep_api.backoffice.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Justificativa minima de 20 caracteres pra ignorar o item.")
public record IgnorarRequest(
        @Schema(example = "Item duplicado de outro fluxo ja em tratamento manual")
                @NotBlank
                @Size(min = 20, max = 10000)
                String justificativa) {}
