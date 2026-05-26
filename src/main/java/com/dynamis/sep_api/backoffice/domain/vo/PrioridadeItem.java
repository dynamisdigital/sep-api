package com.dynamis.sep_api.backoffice.domain.vo;

/**
 * Prioridade do item da fila operacional (Sprint 14 Task 14.1).
 *
 * <p>Ordenacao default em listagens: {@code prioridade DESC} pelo peso (CRITICA primeiro) +
 * {@code dataAbertura ASC}. Como o banco persiste a coluna como VARCHAR, queries que precisem
 * ordenar por severidade devem usar {@link #ordinalPeso()} (ordering em memoria) ou um
 * {@code CASE} explicito no JPQL/SQL — {@code ORDER BY prioridade} lexicografico nao garante a
 * ordem CRITICA &gt; ALTA &gt; MEDIA &gt; BAIXA.
 */
public enum PrioridadeItem {
    BAIXA(1),
    MEDIA(2),
    ALTA(3),
    CRITICA(4);

    private final int peso;

    PrioridadeItem(int peso) {
        this.peso = peso;
    }

    public int ordinalPeso() {
        return peso;
    }
}
