package com.dynamis.sep_api.pix.domain.vo;

/**
 * Status Pix publico exposto ao tomador e a credora nas leituras owner-scoped (Sprint 26 — Gates
 * P1/P3). Recorte minimo do ciclo interno de {@link StatusPixTransferencia}: nao revela estados
 * operacionais intermediarios nem detalhes de provider. Nunca reutilizar em fluxos operacionais.
 */
public enum StatusPixPublico {
    EM_PROCESSAMENTO,
    LIQUIDADO,
    FALHOU,
    CANCELADO
}
