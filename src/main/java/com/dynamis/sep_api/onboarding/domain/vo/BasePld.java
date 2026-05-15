package com.dynamis.sep_api.onboarding.domain.vo;

/**
 * Bases obrigatorias de consulta PLD da Sprint 7 (Lei 9.613/1998 + Resolucao CMN 4.656/2018):
 *
 * <ul>
 *   <li>{@code COAF} — Conselho de Controle de Atividades Financeiras
 *   <li>{@code OFAC} — Office of Foreign Assets Control (sancoes internacionais)
 *   <li>{@code INTERPOL} — Notices da INTERPOL
 *   <li>{@code MTE} — Lista de trabalho escravo do MTE
 * </ul>
 */
public enum BasePld {
    COAF,
    OFAC,
    INTERPOL,
    MTE
}
