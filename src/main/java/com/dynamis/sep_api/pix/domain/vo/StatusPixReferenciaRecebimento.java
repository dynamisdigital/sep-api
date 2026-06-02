package com.dynamis.sep_api.pix.domain.vo;

/**
 * Estados de uma {@code PixReferenciaRecebimento} (Sprint 21 — Epic 15). A referencia liga um
 * {@code txid} gerado pelo SEP a uma parcela; ao receber o Pix correspondente, vira {@code PAGA}.
 *
 * <p>Transicoes: {@code ATIVA -> PAGA | DIVERGENTE | EXPIRADA | CANCELADA}. {@code PAGA} eh terminal
 * (nao volta para {@code ATIVA}).
 */
public enum StatusPixReferenciaRecebimento {
    ATIVA,
    PAGA,
    EXPIRADA,
    CANCELADA,
    DIVERGENTE
}
