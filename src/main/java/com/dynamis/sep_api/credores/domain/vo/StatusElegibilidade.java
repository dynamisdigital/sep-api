package com.dynamis.sep_api.credores.domain.vo;

/**
 * Resultado da decisao de elegibilidade operacional de uma {@code EmpresaCredora}.
 *
 * <p>{@code PENDENTE} e o estado inicial; a decisao deriva do resultado KYB/PLD ja produzido
 * pelo modulo {@code onboarding} (Sprint 16, Task 4), consumido via porta read-only sem acoplar
 * o repositorio interno de onboarding.
 */
public enum StatusElegibilidade {
    PENDENTE,
    ELEGIVEL,
    INELEGIVEL
}
