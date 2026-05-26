package com.dynamis.sep_api.backoffice.domain.vo;

/**
 * Estados de um {@code Reprocesso} (Sprint 14 Task 14.4). Apos {@code SUCESSO} ou {@code FALHA} o
 * registro e imutavel.
 */
public enum StatusReprocesso {
    PENDENTE,
    SUCESSO,
    FALHA
}
