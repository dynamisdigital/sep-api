package com.dynamis.sep_api.onboarding.domain.vo;

/**
 * Situacao cadastral da PJ retornada pelo {@code KybProvider}. So {@code ATIVA} habilita
 * progressao para PLD; demais situacoes reprovam o KYB.
 */
public enum SituacaoCadastral {
    ATIVA,
    SUSPENSA,
    INAPTA,
    BAIXADA,
    DESCONHECIDA;

    public boolean habilitaProgressao() {
        return this == ATIVA;
    }
}
