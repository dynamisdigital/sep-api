package com.dynamis.sep_api.backoffice.web.dto;

import com.dynamis.sep_api.backoffice.application.dto.ItemFilaDetalhe;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Detalhe completo de item da fila operacional, com comentarios e objeto original.")
public record ItemFilaDetalheResponse(
        UUID id,
        TipoItemFila tipo,
        PrioridadeItem prioridade,
        StatusItemFila status,
        TipoEntidadeReferenciada tipoEntidade,
        UUID entidadeId,
        String titulo,
        String descricao,
        UUID atribuidoA,
        OffsetDateTime dataAbertura,
        OffsetDateTime dataResolucao,
        List<ComentarioInternoResponse> comentarios,
        ObjetoOriginalResponse objetoOriginal) {

    public static ItemFilaDetalheResponse from(ItemFilaDetalhe d) {
        return new ItemFilaDetalheResponse(
                d.id(),
                d.tipo(),
                d.prioridade(),
                d.status(),
                d.tipoEntidade(),
                d.entidadeId(),
                d.titulo(),
                d.descricao(),
                d.atribuidoA(),
                d.dataAbertura(),
                d.dataResolucao(),
                d.comentarios().stream().map(ComentarioInternoResponse::from).toList(),
                ObjetoOriginalResponse.from(d.objetoOriginal()));
    }
}
