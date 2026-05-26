package com.dynamis.sep_api.backoffice.web.dto;

import com.dynamis.sep_api.backoffice.domain.model.Reprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Registro auditavel de reprocesso manual disparado.")
public record ReprocessoResponse(
        UUID id,
        UUID itemId,
        Reprocesso.Tipo tipo,
        TipoChamadaProvider tipoChamada,
        String identificadorExterno,
        StatusReprocesso status,
        String resultado,
        OffsetDateTime dataDisparo,
        UUID disparadoPor) {

    public static ReprocessoResponse from(Reprocesso r) {
        return new ReprocessoResponse(
                r.getId(),
                r.getItemId(),
                r.getTipo(),
                r.getTipoChamada(),
                r.getIdentificadorExterno(),
                r.getStatus(),
                r.getResultado(),
                r.getDataDisparo(),
                r.getDisparadoPor());
    }
}
