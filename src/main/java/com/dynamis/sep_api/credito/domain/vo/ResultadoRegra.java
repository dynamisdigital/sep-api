package com.dynamis.sep_api.credito.domain.vo;

/**
 * Resultado da avaliacao de uma regra do motor de credito (Sprint 8 Task 8.2).
 *
 * <ul>
 *   <li>{@link #PASSOU}: regra atendida — sem penalidade no score.
 *   <li>{@link #FALHOU}: regra violada — penalidade alta; pode ser bloqueante (ex.: onboarding nao
 *       aprovado leva direto a {@code REJEITADA}).
 *   <li>{@link #PENDENTE}: dado insuficiente pra concluir — penalidade menor; motor sugere
 *       {@code EM_ANALISE}.
 * </ul>
 */
public enum ResultadoRegra {
    PASSOU,
    FALHOU,
    PENDENTE
}
