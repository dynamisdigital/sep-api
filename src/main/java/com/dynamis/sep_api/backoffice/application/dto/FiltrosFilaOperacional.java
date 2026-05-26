package com.dynamis.sep_api.backoffice.application.dto;

import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Filtros opcionais combinaveis para listagem da fila (Sprint 14 Task 14.3). Qualquer campo
 * {@code null} indica "sem filtro".
 */
public record FiltrosFilaOperacional(
        TipoItemFila tipo,
        PrioridadeItem prioridade,
        StatusItemFila status,
        OffsetDateTime dataAberturaDe,
        OffsetDateTime dataAberturaAte,
        UUID atribuidoA) {

    public static FiltrosFilaOperacional vazio() {
        return new FiltrosFilaOperacional(null, null, null, null, null, null);
    }
}
