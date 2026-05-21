package com.dynamis.sep_api.contratos.domain.vo;

import java.util.Set;

/**
 * Estados do ciclo de formalizacao contratual (Sprint 10 + Sprint 11).
 *
 * <p>Maquina de estados:
 *
 * <pre>
 *   GERADO            -> AGUARDANDO_ACEITE | CANCELADO
 *   AGUARDANDO_ACEITE -> AGUARDANDO_ACEITE (regeneracao) | ACEITO | CANCELADO
 *   ACEITO            -> EM_ASSINATURA (Sprint 11)
 *   EM_ASSINATURA     -> ASSINADO | RECUSADO (Sprint 11)
 *   ASSINADO / RECUSADO / CANCELADO = finais
 * </pre>
 *
 * <p>Sprint 11 (assinatura digital + CCB) introduz {@code RECUSADO} e habilita as transicoes
 * {@code ACEITO -> EM_ASSINATURA -> ASSINADO|RECUSADO}.
 */
public enum StatusFormalizacao {
    GERADO,
    AGUARDANDO_ACEITE,
    ACEITO,
    EM_ASSINATURA,
    ASSINADO,
    RECUSADO,
    CANCELADO;

    private static final Set<StatusFormalizacao> FINAIS = Set.of(ASSINADO, RECUSADO, CANCELADO);
    private static final Set<StatusFormalizacao> PERMITEM_NOVA_VERSAO = Set.of(GERADO, AGUARDANDO_ACEITE);
    private static final Set<StatusFormalizacao> PERMITEM_CANCELAMENTO = Set.of(GERADO, AGUARDANDO_ACEITE);

    public boolean isFinal() {
        return FINAIS.contains(this);
    }

    public boolean permiteNovaVersao() {
        return PERMITEM_NOVA_VERSAO.contains(this);
    }

    public boolean permiteAceite() {
        return this == AGUARDANDO_ACEITE;
    }

    public boolean permiteCancelamento() {
        return PERMITEM_CANCELAMENTO.contains(this);
    }

    public boolean permiteEnvioAssinatura() {
        return this == ACEITO;
    }

    public boolean permiteFinalizarAssinatura() {
        return this == EM_ASSINATURA;
    }
}
