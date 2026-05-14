package com.dynamis.sep_api.onboarding.application.port.out.dto;

import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;

import java.util.Set;
import java.util.UUID;

/**
 * Requisicao enviada ao {@link com.dynamis.sep_api.onboarding.application.port.out.BackgroundCheckProvider}
 * para consultar bases PLD.
 *
 * <p>{@code documento} = CPF (11) para {@code PESSOA}/{@code REPRESENTANTE}; CNPJ (14) para
 * {@code EMPRESA}. {@code bases} aceita um subconjunto; PLD obrigatorio usa as 4 (COAF, OFAC,
 * INTERPOL, MTE).
 */
public record RequisicaoPld(UUID solicitacaoId, AlvoPld alvoTipo, String nome, String documento, Set<BasePld> bases) {

    /** Conveniencia: monta a requisicao com as 4 bases obrigatorias da Sprint 7. */
    public static RequisicaoPld comBasesObrigatorias(
            UUID solicitacaoId, AlvoPld alvoTipo, String nome, String documento) {
        return new RequisicaoPld(
                solicitacaoId,
                alvoTipo,
                nome,
                documento,
                Set.of(BasePld.COAF, BasePld.OFAC, BasePld.INTERPOL, BasePld.MTE));
    }
}
