package com.dynamis.sep_api.backoffice.web.dto;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Resumo do objeto de dominio referenciado pelo item da fila.")
public record ObjetoOriginalResponse(
        TipoEntidadeReferenciada tipoEntidade, UUID entidadeId, String status, String descricaoCurta) {

    public static ObjetoOriginalResponse from(ObjetoOriginalResumo o) {
        if (o == null) {
            return null;
        }
        return new ObjetoOriginalResponse(o.tipoEntidade(), o.entidadeId(), o.status(), o.descricaoCurta());
    }
}
