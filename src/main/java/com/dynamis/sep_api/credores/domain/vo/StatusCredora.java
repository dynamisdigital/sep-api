package com.dynamis.sep_api.credores.domain.vo;

/**
 * Status do ciclo de vida cadastral de uma {@code EmpresaCredora}.
 *
 * <pre>
 *   CADASTRADA -> ATIVA      (apos elegibilidade ELEGIVEL)
 *   ATIVA      -> SUSPENSA   (bloqueio operacional reversivel)
 *   SUSPENSA   -> ATIVA      (reativacao)
 * </pre>
 *
 * <p>{@code CADASTRADA} representa a credora recem-criada, ainda pendente de decisao de
 * elegibilidade. Somente {@code ATIVA} habilita operacoes da jornada credora (aportes,
 * carteira) nas sprints seguintes.
 */
public enum StatusCredora {
    CADASTRADA,
    ATIVA,
    SUSPENSA
}
