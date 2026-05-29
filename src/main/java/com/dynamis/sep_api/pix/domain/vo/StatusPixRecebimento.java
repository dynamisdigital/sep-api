package com.dynamis.sep_api.pix.domain.vo;

/**
 * Estados de um {@code PixRecebimento} identificado por evento de provider (Epic 15 / Sprint 19).
 *
 * <p>Conciliacao automatica de parcela fica para Sprints 20/21; aqui o recebimento apenas nasce e
 * pode ser classificado como {@code CONCILIADO} ou {@code NAO_IDENTIFICADO}.
 */
public enum StatusPixRecebimento {
    RECEBIDO,
    EM_PROCESSAMENTO,
    CONCILIADO,
    NAO_IDENTIFICADO,
    FALHOU
}
