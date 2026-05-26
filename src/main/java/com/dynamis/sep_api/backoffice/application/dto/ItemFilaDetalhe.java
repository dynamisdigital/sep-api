package com.dynamis.sep_api.backoffice.application.dto;

import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ItemFilaDetalhe(
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
        List<ComentarioInternoSummary> comentarios,
        ObjetoOriginalResumo objetoOriginal) {

    public static ItemFilaDetalhe de(
            ItemFilaOperacional item, List<ComentarioInternoSummary> comentarios, ObjetoOriginalResumo objetoOriginal) {
        return new ItemFilaDetalhe(
                item.getId(),
                item.getTipo(),
                item.getPrioridade(),
                item.getStatus(),
                item.getTipoEntidade(),
                item.getEntidadeId(),
                item.getTitulo(),
                item.getDescricao(),
                item.getAtribuidoA(),
                item.getDataAbertura(),
                item.getDataResolucao(),
                comentarios,
                objetoOriginal);
    }
}
