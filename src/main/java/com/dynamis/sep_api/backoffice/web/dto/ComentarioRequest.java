package com.dynamis.sep_api.backoffice.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Comentario interno a registrar no item da fila.")
public record ComentarioRequest(
        @Schema(example = "Tomador entrou em contato; aguardando documento") @NotBlank @Size(min = 1, max = 10000)
                String conteudo) {}
