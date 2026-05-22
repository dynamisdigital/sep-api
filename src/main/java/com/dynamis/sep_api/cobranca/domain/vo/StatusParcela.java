package com.dynamis.sep_api.cobranca.domain.vo;

import java.util.Set;

/**
 * Estados da {@code ParcelaCobranca} (Sprint 12).
 *
 * <p>Maquina de estados:
 *
 * <pre>
 *   PENDENTE          -> PARCIALMENTE_PAGA | PAGA | ATRASADA
 *   PARCIALMENTE_PAGA -> PARCIALMENTE_PAGA | PAGA
 *   ATRASADA          -> PARCIALMENTE_PAGA | PAGA | INADIMPLENTE (Sprint 13)
 *   PAGA              = final
 *   INADIMPLENTE      = reservado pra Sprint 13; nao alcancado pelo job desta sprint
 * </pre>
 */
public enum StatusParcela {
    PENDENTE,
    PARCIALMENTE_PAGA,
    PAGA,
    ATRASADA,
    INADIMPLENTE;

    private static final Set<StatusParcela> FINAIS = Set.of(PAGA);
    // INADIMPLENTE NAO permite recebimento nesta sprint — regra fica pra Sprint 13
    // (recuperacao/renegociacao define se reabre).
    private static final Set<StatusParcela> PERMITEM_RECEBIMENTO = Set.of(PENDENTE, PARCIALMENTE_PAGA, ATRASADA);

    public boolean isFinal() {
        return FINAIS.contains(this);
    }

    public boolean permiteRecebimento() {
        return PERMITEM_RECEBIMENTO.contains(this);
    }

    public boolean permiteMarcarAtrasada() {
        return this == PENDENTE;
    }
}
