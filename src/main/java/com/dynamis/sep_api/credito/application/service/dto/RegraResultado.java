package com.dynamis.sep_api.credito.application.service.dto;

import com.dynamis.sep_api.credito.domain.vo.ResultadoRegra;

/**
 * Resultado de uma regra avaliada — produzido por {@code RegraCredito.avaliar(...)} (Task 8.2) e
 * persistido como {@code RegraCreditoAvaliada} na trilha auditavel (Task 8.3).
 *
 * <p>{@code bloqueante=true} indica que a falha leva direto a {@code REJEITADA}, independente do
 * score agregado.
 */
public record RegraResultado(String nome, ResultadoRegra resultado, String motivo, boolean bloqueante) {

    public static RegraResultado passou(String nome) {
        return new RegraResultado(nome, ResultadoRegra.PASSOU, null, false);
    }

    public static RegraResultado falhou(String nome, String motivo) {
        return new RegraResultado(nome, ResultadoRegra.FALHOU, motivo, false);
    }

    public static RegraResultado falhouBloqueante(String nome, String motivo) {
        return new RegraResultado(nome, ResultadoRegra.FALHOU, motivo, true);
    }

    public static RegraResultado pendente(String nome, String motivo) {
        return new RegraResultado(nome, ResultadoRegra.PENDENTE, motivo, false);
    }
}
