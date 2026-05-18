package com.dynamis.sep_api.credito.domain.vo;

import java.util.Set;

/**
 * Estados de uma {@code PropostaCredito} (Sprint 8 — analise de credito interna).
 *
 * <p>Maquina de estados:
 *
 * <pre>
 *   EM_ANALISE -> PRE_APROVADA | EM_ANALISE | REJEITADA | PENDENCIA
 *   PRE_APROVADA -> APROVADA | REJEITADA | PENDENCIA
 *   PENDENCIA -> APROVADA | REJEITADA | EM_ANALISE
 *   APROVADA / REJEITADA = finais
 * </pre>
 *
 * <p>{@code APROVADA} e {@code REJEITADA} sao estados finais. {@code PRE_APROVADA} e sugerida pelo
 * motor de regras quando score >= threshold de pre-aprovacao mas ainda exige parecer manual.
 * {@code APROVADA} so e atingida via {@link DecisaoParecer#APROVAR} pelo operador financeiro.
 */
public enum StatusProposta {
    EM_ANALISE,
    PRE_APROVADA,
    APROVADA,
    REJEITADA,
    PENDENCIA;

    private static final Set<StatusProposta> FINAIS = Set.of(APROVADA, REJEITADA);

    public boolean isFinal() {
        return FINAIS.contains(this);
    }
}
