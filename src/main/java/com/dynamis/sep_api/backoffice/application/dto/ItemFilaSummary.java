package com.dynamis.sep_api.backoffice.application.dto;

import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Projecao de listagem da fila (Sprint 14 Task 14.3) — sem comentarios e sem descricao longa. */
public record ItemFilaSummary(
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

    public static ItemFilaSummary de(ItemFilaOperacional item) {
        return new ItemFilaSummary(
                item.getId(),
                item.getTipo(),
                item.getPrioridade(),
                item.getStatus(),
                item.getTipoEntidade(),
                item.getEntidadeId(),
                item.getTitulo(),
                item.getAtribuidoA(),
                item.getDataAbertura(),
                item.getDataResolucao());
    }
}
