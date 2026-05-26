package com.dynamis.sep_api.backoffice.domain.event;

import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;

import java.util.UUID;

/**
 * Publicado pelo {@code CriarItemFilaOperacionalService} quando um novo item entra na fila
 * (Sprint 14 Task 14.2). Consumido pelo {@code BackofficeAuditListener} (Task 14.8).
 */
public record ItemFilaCriadoEvent(
        UUID itemId,
        TipoItemFila tipo,
        PrioridadeItem prioridade,
        TipoEntidadeReferenciada tipoEntidade,
        UUID entidadeId) {}
