package com.dynamis.sep_api.pix.domain.vo;

/**
 * Estados de uma {@code PixTransferencia} (Epic 15 / Sprint 19 — foundation, sem desembolso real).
 *
 * <p>Fluxo esperado: {@code CRIADA -> SOLICITADA -> PROCESSANDO -> CONCLUIDA}, com {@code FALHOU} a
 * partir de qualquer estado ativo e {@code CANCELADA} apenas antes de solicitar ao provider.
 */
public enum StatusPixTransferencia {
    CRIADA,
    SOLICITADA,
    PROCESSANDO,
    CONCLUIDA,
    FALHOU,
    CANCELADA;

    /**
     * Estados que "ocupam" o contrato para fins de desembolso (Sprint 20): enquanto houver uma
     * transferencia nestes estados, o contrato nao aceita um novo desembolso. {@code FALHOU} e
     * {@code CANCELADA} liberam o contrato para nova tentativa.
     */
    public boolean ocupaContrato() {
        return this == CRIADA || this == SOLICITADA || this == PROCESSANDO || this == CONCLUIDA;
    }
}
