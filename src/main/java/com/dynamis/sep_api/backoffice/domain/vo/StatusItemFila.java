package com.dynamis.sep_api.backoffice.domain.vo;

import java.util.Set;

/**
 * Estados do {@code ItemFilaOperacional} (Sprint 14 Task 14.1).
 *
 * <pre>
 *   ABERTO        -> EM_TRATAMENTO | IGNORADO
 *   EM_TRATAMENTO -> RESOLVIDO | IGNORADO
 *   RESOLVIDO     = final
 *   IGNORADO      = final
 * </pre>
 *
 * <p>O par UNIQUE parcial em {@code item_fila_operacional} considera apenas itens ativos
 * (ABERTO/EM_TRATAMENTO), permitindo reabrir um novo item para a mesma entidade apos resolucao
 * legitima.
 */
public enum StatusItemFila {
    ABERTO,
    EM_TRATAMENTO,
    RESOLVIDO,
    IGNORADO;

    private static final Set<StatusItemFila> ATIVOS = Set.of(ABERTO, EM_TRATAMENTO);
    private static final Set<StatusItemFila> FINAIS = Set.of(RESOLVIDO, IGNORADO);

    public boolean isAtivo() {
        return ATIVOS.contains(this);
    }

    public boolean isFinal() {
        return FINAIS.contains(this);
    }

    public boolean permiteAssumir() {
        return this == ABERTO;
    }

    public boolean permiteResolver() {
        return this == EM_TRATAMENTO;
    }

    public boolean permiteIgnorar() {
        return this == ABERTO || this == EM_TRATAMENTO;
    }
}
