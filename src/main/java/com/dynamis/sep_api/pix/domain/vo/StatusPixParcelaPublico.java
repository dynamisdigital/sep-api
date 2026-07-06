package com.dynamis.sep_api.pix.domain.vo;

/**
 * Status Pix publico da parcela exposto ao tomador (Sprint 26 — Gate P2). Derivado da referencia de
 * recebimento e do recebimento correlacionado por precedencia (ver {@code StatusPixParcelaPublicoMapper});
 * nao revela {@code txid}, copia-cola, motivo tecnico ou IDs internos. Nunca reutilizar em fluxos
 * operacionais.
 */
public enum StatusPixParcelaPublico {
    AGUARDANDO,
    EM_PROCESSAMENTO,
    LIQUIDADO,
    DIVERGENTE,
    FALHOU,
    EXPIRADO,
    CANCELADO
}
