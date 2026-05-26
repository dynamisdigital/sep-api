package com.dynamis.sep_api.backoffice.web.dto;

import com.dynamis.sep_api.backoffice.application.dto.ItemFilaSummary;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Resumo de item da fila operacional para listagem.")
public record ItemFilaResponse(
        UUID id,
        TipoItemFila tipo,
        PrioridadeItem prioridade,
        StatusItemFila status,
        TipoEntidadeReferenciada tipoEntidade,
        UUID entidadeId,
        String titulo,
        UUID atribuidoA,
        OffsetDateTime dataAbertura,
        OffsetDateTime dataResolucao) {

    public static ItemFilaResponse from(ItemFilaSummary s) {
        return new ItemFilaResponse(
                s.id(),
                s.tipo(),
                s.prioridade(),
                s.status(),
                s.tipoEntidade(),
                s.entidadeId(),
                s.titulo(),
                s.atribuidoA(),
                s.dataAbertura(),
                s.dataResolucao());
    }
}
