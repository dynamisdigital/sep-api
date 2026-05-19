package com.dynamis.sep_api.credito.domain.vo;

import java.util.Set;

/**
 * Estados do {@code ConsentimentoOpenFinance} (Sprint 9 — Open Finance Brasil).
 *
 * <p>Maquina de estados:
 *
 * <pre>
 *   PENDENTE -> AUTORIZADO | NEGADO | EXPIRADO
 *   AUTORIZADO / NEGADO / EXPIRADO = finais
 * </pre>
 *
 * <p>Apenas {@link #AUTORIZADO} habilita consulta de movimentacao via {@code OpenFinanceProvider}.
 */
public enum StatusConsentimento {
    PENDENTE,
    AUTORIZADO,
    NEGADO,
    EXPIRADO;

    private static final Set<StatusConsentimento> FINAIS = Set.of(AUTORIZADO, NEGADO, EXPIRADO);

    public boolean isFinal() {
        return FINAIS.contains(this);
    }

    public boolean permiteConsulta() {
        return this == AUTORIZADO;
    }
}
