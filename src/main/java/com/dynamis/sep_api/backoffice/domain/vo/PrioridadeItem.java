package com.dynamis.sep_api.backoffice.domain.vo;

/**
 * Prioridade do item da fila operacional (Sprint 14 Task 14.1). Ordenacao default em listagens:
 * {@code prioridade DESC} (CRITICA primeiro) + {@code dataAbertura ASC}.
 */
public enum PrioridadeItem {
    BAIXA,
    MEDIA,
    ALTA,
    CRITICA
}
