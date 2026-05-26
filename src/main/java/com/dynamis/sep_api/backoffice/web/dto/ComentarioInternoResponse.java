package com.dynamis.sep_api.backoffice.web.dto;

import com.dynamis.sep_api.backoffice.application.dto.ComentarioInternoSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Comentario interno vinculado a item da fila.")
public record ComentarioInternoResponse(UUID id, UUID autorId, String conteudo, OffsetDateTime dataCriacao) {

    public static ComentarioInternoResponse from(ComentarioInternoSummary s) {
        return new ComentarioInternoResponse(s.id(), s.autorId(), s.conteudo(), s.dataCriacao());
    }
}
