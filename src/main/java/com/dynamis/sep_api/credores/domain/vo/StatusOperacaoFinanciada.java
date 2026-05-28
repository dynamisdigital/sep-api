package com.dynamis.sep_api.credores.domain.vo;

/**
 * Status de uma {@code OperacaoFinanciada} na carteira da credora.
 *
 * <p>{@code ASSOCIADA}: vinculo operacional criado por associacao assistida (Sprint 17), ainda sem
 * movimentacao financeira real. {@code ENCERRADA}: operacao concluida. Sprint 17 nao move recurso
 * financeiro — o status reflete a associacao operacional, nao liquidacao.
 */
public enum StatusOperacaoFinanciada {
    ASSOCIADA,
    ENCERRADA
}
