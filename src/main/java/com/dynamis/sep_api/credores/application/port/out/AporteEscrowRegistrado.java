package com.dynamis.sep_api.credores.application.port.out;

import java.util.Objects;

/**
 * Resultado do registro do aporte no escrow (Sprint 29 Task 29.2). {@code referenciaEscrow} e a
 * referencia INTERNA da movimentacao no escrow — nunca exposta em contrato publico. {@code status}
 * vem como String para nao acoplar {@code credores} ao enum do {@code escrow} (mesmo racional de
 * {@link ContratoCarteiraView}).
 */
public record AporteEscrowRegistrado(String referenciaEscrow, String status) {

    public AporteEscrowRegistrado {
        Objects.requireNonNull(referenciaEscrow, "referenciaEscrow obrigatoria");
        Objects.requireNonNull(status, "status obrigatorio");
    }
}
