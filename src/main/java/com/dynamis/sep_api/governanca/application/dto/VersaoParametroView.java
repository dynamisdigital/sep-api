package com.dynamis.sep_api.governanca.application.dto;

import com.dynamis.sep_api.governanca.domain.model.VersaoParametroOperacional;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Projecao read-only de uma entrada do historico de um parametro operacional. */
public record VersaoParametroView(
        int versao,
        String valorAnterior,
        String valorNovo,
        UUID atorId,
        String justificativa,
        OffsetDateTime dataCriacao) {

    public static VersaoParametroView de(VersaoParametroOperacional v) {
        return new VersaoParametroView(
                v.getVersao(),
                v.getValorAnterior(),
                v.getValorNovo(),
                v.getAtorId(),
                v.getJustificativa(),
                v.getDataCriacao());
    }
}
