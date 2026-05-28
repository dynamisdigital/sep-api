package com.dynamis.sep_api.credores.domain.vo;

/**
 * Status de uma {@code OperacaoFinanciada} na carteira da credora.
 *
 * <p>{@code ATIVA}: operacao vigente na carteira. {@code ENCERRADA}: concluida/liquidada. Sprint
 * 17 nao move recurso financeiro real — o status reflete a associacao operacional, nao liquidacao.
 */
public enum StatusOperacaoFinanciada {
    ATIVA,
    ENCERRADA
}
