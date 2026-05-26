package com.dynamis.sep_api.cobranca.domain.vo;

import java.util.Set;

/**
 * Estados da {@code ParcelaCobranca} (Sprint 12 + Sprint 13).
 *
 * <p>Maquina de estados:
 *
 * <pre>
 *   PENDENTE          -> PARCIALMENTE_PAGA | PAGA | ATRASADA
 *   PARCIALMENTE_PAGA -> PARCIALMENTE_PAGA | PAGA
 *   ATRASADA          -> PARCIALMENTE_PAGA | PAGA | INADIMPLENTE | EM_NEGOCIACAO
 *   INADIMPLENTE      -> EM_NEGOCIACAO (renegociacao eh caminho unico de saida)
 *   EM_NEGOCIACAO     -> ATRASADA | INADIMPLENTE (recusa) | RENEGOCIADA (aceite)
 *   RENEGOCIADA       = final (parcela substituida por nova agenda)
 *   PAGA              = final
 * </pre>
 */
public enum StatusParcela {
    PENDENTE,
    PARCIALMENTE_PAGA,
    PAGA,
    ATRASADA,
    INADIMPLENTE,
    EM_NEGOCIACAO,
    RENEGOCIADA;

    private static final Set<StatusParcela> FINAIS = Set.of(PAGA, RENEGOCIADA);

    // INADIMPLENTE e EM_NEGOCIACAO bloqueiam recebimento manual nesta sprint:
    // - INADIMPLENTE forca renegociacao como caminho operacional unico.
    // - EM_NEGOCIACAO impede pagamento avulso enquanto proposta esta em vigencia.
    private static final Set<StatusParcela> PERMITEM_RECEBIMENTO = Set.of(PENDENTE, PARCIALMENTE_PAGA, ATRASADA);

    // Apenas ATRASADA pode transicionar pra INADIMPLENTE (job 90+ dias da Sprint 13).
    private static final Set<StatusParcela> PERMITEM_MARCAR_INADIMPLENTE = Set.of(ATRASADA);

    // ATRASADA e INADIMPLENTE podem entrar em renegociacao (proposta do financeiro).
    private static final Set<StatusParcela> PERMITEM_INICIAR_RENEGOCIACAO = Set.of(ATRASADA, INADIMPLENTE);

    public boolean isFinal() {
        return FINAIS.contains(this);
    }

    public boolean permiteRecebimento() {
        return PERMITEM_RECEBIMENTO.contains(this);
    }

    public boolean permiteMarcarAtrasada() {
        return this == PENDENTE;
    }

    public boolean permiteMarcarInadimplente() {
        return PERMITEM_MARCAR_INADIMPLENTE.contains(this);
    }

    public boolean permiteIniciarRenegociacao() {
        return PERMITEM_INICIAR_RENEGOCIACAO.contains(this);
    }
}
