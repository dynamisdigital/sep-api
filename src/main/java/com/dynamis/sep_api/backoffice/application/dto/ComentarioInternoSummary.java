package com.dynamis.sep_api.backoffice.application.dto;

import com.dynamis.sep_api.backoffice.domain.model.ComentarioInterno;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ComentarioInternoSummary(UUID id, UUID autorId, String conteudo, OffsetDateTime dataCriacao) {

    public static ComentarioInternoSummary de(ComentarioInterno c) {
        return new ComentarioInternoSummary(c.getId(), c.getAutorId(), c.getConteudo(), c.getDataCriacao());
    }
}
