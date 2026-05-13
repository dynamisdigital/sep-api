package com.dynamis.sep_api.onboarding.domain.vo;

import java.util.Set;

/**
 * Status possiveis de uma {@code SolicitacaoOnboarding} KYC PF.
 *
 * <p>Maquina de estados:
 *
 * <pre>
 *   INICIADO -> DOCUMENTOS_RECEBIDOS
 *   DOCUMENTOS_RECEBIDOS -> EM_VERIFICACAO
 *   EM_VERIFICACAO -> APROVADO | REPROVADO | PENDENCIA
 * </pre>
 *
 * <p>Status finais: {@code APROVADO}, {@code REPROVADO}, {@code PENDENCIA}.
 */
public enum StatusOnboarding {
    INICIADO,
    DOCUMENTOS_RECEBIDOS,
    EM_VERIFICACAO,
    APROVADO,
    REPROVADO,
    PENDENCIA;

    private static final Set<StatusOnboarding> FINAIS = Set.of(APROVADO, REPROVADO, PENDENCIA);

    private static final Set<StatusOnboarding> ATIVOS =
            Set.of(INICIADO, DOCUMENTOS_RECEBIDOS, EM_VERIFICACAO, APROVADO, PENDENCIA);

    public boolean isFinal() {
        return FINAIS.contains(this);
    }

    /**
     * Status considerados "ativos" para fins de bloqueio de CPF duplicado. Inclui status finais que
     * mantem a vinculacao ao CPF ({@code APROVADO}, {@code PENDENCIA}). {@code REPROVADO} libera o
     * CPF para nova tentativa.
     */
    public boolean isAtivo() {
        return ATIVOS.contains(this);
    }
}
