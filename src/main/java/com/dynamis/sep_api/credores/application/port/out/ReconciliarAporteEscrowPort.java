package com.dynamis.sep_api.credores.application.port.out;

/**
 * Porta de saida para reconciliar a movimentacao de aporte no escrow (Sprint 29 Task 29.5).
 * Consumer-driven: a reconciliacao do aporte so precisa confirmar liquidacao ou falha da
 * movimentacao ja registrada por {@link RegistrarAporteEscrowPort}. Ambas idempotentes no escrow.
 * Falhas chegam como {@link com.dynamis.sep_api.credores.domain.exception.AporteEscrowException}
 * sanitizada.
 */
public interface ReconciliarAporteEscrowPort {

    /** Liquida a movimentacao do aporte no escrow (credita a wallet na primeira liquidacao). */
    void liquidar(String referenciaEscrow);

    /** Marca falha terminal da movimentacao do aporte no escrow (sem credito de saldo). */
    void falhar(String referenciaEscrow);
}
