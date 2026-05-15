package com.dynamis.sep_api.onboarding.domain.vo;

import java.util.Set;

/**
 * Status possiveis de uma {@code SolicitacaoOnboarding} (PF KYC + PJ KYB + PLD).
 *
 * <p>Maquina de estados:
 *
 * <pre>
 *   INICIADO -> DOCUMENTOS_RECEBIDOS
 *   DOCUMENTOS_RECEBIDOS -> EM_VERIFICACAO
 *   EM_VERIFICACAO -> APROVADO | REPROVADO | PENDENCIA
 *   APROVADO -> APROVADO_FINAL | REPROVADO_PLD     (apos PLD na Sprint 7)
 * </pre>
 *
 * <p>Status finais: {@code APROVADO}, {@code REPROVADO}, {@code PENDENCIA}, {@code APROVADO_FINAL},
 * {@code REPROVADO_PLD}.
 *
 * <p>Sprint 7: {@code APROVADO} passa a representar aprovacao KYC/KYB <em>antes</em> do PLD;
 * {@code APROVADO_FINAL} representa onboarding apto pra consumo da Sprint 8 (credito).
 * {@code REPROVADO_PLD} bloqueia onboarding por hit em base PLD.
 */
public enum StatusOnboarding {
    INICIADO,
    DOCUMENTOS_RECEBIDOS,
    EM_VERIFICACAO,
    APROVADO,
    REPROVADO,
    PENDENCIA,
    APROVADO_FINAL,
    REPROVADO_PLD;

    private static final Set<StatusOnboarding> FINAIS =
            Set.of(APROVADO, REPROVADO, PENDENCIA, APROVADO_FINAL, REPROVADO_PLD);

    private static final Set<StatusOnboarding> ATIVOS =
            Set.of(INICIADO, DOCUMENTOS_RECEBIDOS, EM_VERIFICACAO, APROVADO, PENDENCIA, APROVADO_FINAL);

    public boolean isFinal() {
        return FINAIS.contains(this);
    }

    /**
     * Status considerados "ativos" para fins de bloqueio de documento duplicado (CPF/CNPJ). Inclui
     * finais que mantem a vinculacao ao documento ({@code APROVADO}, {@code PENDENCIA},
     * {@code APROVADO_FINAL}). {@code REPROVADO} e {@code REPROVADO_PLD} liberam o documento para
     * nova tentativa.
     */
    public boolean isAtivo() {
        return ATIVOS.contains(this);
    }
}
