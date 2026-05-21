package com.dynamis.sep_api.contratos.domain.vo;

import java.util.Set;

/**
 * Estados do ciclo de formalizacao contratual (Sprint 10).
 *
 * <p>Maquina de estados:
 *
 * <pre>
 *   GERADO            -> AGUARDANDO_ACEITE | CANCELADO
 *   AGUARDANDO_ACEITE -> AGUARDANDO_ACEITE (regeneracao) | ACEITO | CANCELADO
 *   ACEITO            -> EM_ASSINATURA (Sprint 11)
 *   EM_ASSINATURA     -> ASSINADO (Sprint 11)
 *   ASSINADO / CANCELADO = finais
 * </pre>
 *
 * <p>{@code EM_ASSINATURA} e {@code ASSINADO} sao reservados para a Sprint 11 (assinatura digital
 * + CCB). Esta Sprint 10 cobre apenas ate {@code ACEITO}.
 */
public enum StatusFormalizacao {
    GERADO,
    AGUARDANDO_ACEITE,
    ACEITO,
    EM_ASSINATURA,
    ASSINADO,
    CANCELADO;

    private static final Set<StatusFormalizacao> FINAIS = Set.of(ASSINADO, CANCELADO);
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
}
