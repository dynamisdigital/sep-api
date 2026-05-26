package com.dynamis.sep_api.cobranca.domain.vo;

import java.util.Set;

/**
 * Estados de uma {@code Renegociacao} (Sprint 13 Task 13.2).
 *
 * <p>Maquina de estados:
 *
 * <pre>
 *   PROPOSTA  -> ACEITA | RECUSADA | EXPIRADA
 *   ACEITA    = final
 *   RECUSADA  = final
 *   EXPIRADA  = final
 * </pre>
 *
 * <p>Conjunto fechado — spec referencia "sealed" como restricao de transicao, nao como recurso de
 * linguagem (enum nao pode ser {@code sealed}).
 */
public enum StatusRenegociacao {
    PROPOSTA,
    ACEITA,
    RECUSADA,
    EXPIRADA;

    private static final Set<StatusRenegociacao> FINAIS = Set.of(ACEITA, RECUSADA, EXPIRADA);

    public boolean isFinal() {
        return FINAIS.contains(this);
    }
}
