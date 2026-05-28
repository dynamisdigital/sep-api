package com.dynamis.sep_api.credores.application.dto;

import com.dynamis.sep_api.credores.domain.model.InteresseCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusInteresseCredora;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Projecao read-only de uma manifestacao de interesse da credora. */
public record InteresseView(UUID id, UUID oportunidadeId, StatusInteresseCredora status, OffsetDateTime dataCriacao) {

    public static InteresseView de(InteresseCredora i) {
        return new InteresseView(i.getId(), i.getOportunidadeId(), i.getStatus(), i.getDataCriacao());
    }
}
