package com.dynamis.sep_api.credito.application.service.dto;

import com.dynamis.sep_api.credito.domain.vo.ResultadoRegra;

/**
 * Resultado de uma regra avaliada — produzido por {@code RegraCredito.avaliar(...)} (Task 8.2) e
 * persistido como {@code RegraCreditoAvaliada} na trilha auditavel (Task 8.3).
 *
 * <p>{@code bloqueante=true} indica que a falha leva direto a {@code REJEITADA}, independente do
 * score agregado.
 *
 * <p>Sprint 9 Task 9.4: {@code ajusteScore} permite que regras adicionem bonus positivo (ex.:
 * {@code RegraOpenFinanceMovimentacao} bonifica score quando movimentacao bancaria atende
 * thresholds) ou penalidade adicional alem da padrao do motor. {@code 0} (default) preserva
 * comportamento original — todas as regras Sprint 8 sao neutras.
 */
public record RegraResultado(
        String nome, ResultadoRegra resultado, String motivo, boolean bloqueante, int ajusteScore) {

    public static RegraResultado passou(String nome) {
        return new RegraResultado(nome, ResultadoRegra.PASSOU, null, false, 0);
    }

    public static RegraResultado passouComBonus(String nome, int bonus) {
        if (bonus < 0) {
            throw new IllegalArgumentException("bonus deve ser >= 0; use falhouComAjuste para penalidade extra");
        }
        return new RegraResultado(nome, ResultadoRegra.PASSOU, null, false, bonus);
    }

    public static RegraResultado falhou(String nome, String motivo) {
        return new RegraResultado(nome, ResultadoRegra.FALHOU, motivo, false, 0);
    }

    public static RegraResultado falhouComPenalidadeExtra(String nome, String motivo, int penalidadeExtra) {
        if (penalidadeExtra < 0) {
            throw new IllegalArgumentException("penalidadeExtra deve ser >= 0");
        }
        return new RegraResultado(nome, ResultadoRegra.FALHOU, motivo, false, -penalidadeExtra);
    }

    public static RegraResultado falhouBloqueante(String nome, String motivo) {
        return new RegraResultado(nome, ResultadoRegra.FALHOU, motivo, true, 0);
    }

    public static RegraResultado pendente(String nome, String motivo) {
        return new RegraResultado(nome, ResultadoRegra.PENDENTE, motivo, false, 0);
    }
}
