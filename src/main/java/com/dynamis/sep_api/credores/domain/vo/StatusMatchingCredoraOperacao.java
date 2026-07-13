package com.dynamis.sep_api.credores.domain.vo;

/**
 * Status da sugestao de matching credora-operacao (Sprint 30).
 *
 * <pre>
 *   SUGERIDA -> CONFIRMADA   (decisao assistida de financeiro/admin, terminal)
 *            -> REJEITADA    (decisao assistida de financeiro/admin, terminal)
 * </pre>
 *
 * <p>Nenhuma sugestao e confirmada automaticamente: a transicao terminal exige decisao de
 * operador com step-up estrito. Estados terminais nao admitem nova transicao.
 */
public enum StatusMatchingCredoraOperacao {
    SUGERIDA,
    CONFIRMADA,
    REJEITADA;

    /** Terminal = decidido; nao admite nova transicao (replay de decisao -> conflito 409). */
    public boolean terminal() {
        return this != SUGERIDA;
    }
}
