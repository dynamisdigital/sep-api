package com.dynamis.sep_api.escrow.domain.vo;

/**
 * Tipos de movimentacao na conta escrow segregada (PRD §11, ADR 0005).
 *
 * <p>Sealed para forcar que todo handler trate todos os casos via {@code switch} exhaustivo.
 */
public sealed interface TipoMovimentacao
        permits TipoMovimentacao.Aporte,
                TipoMovimentacao.Desembolso,
                TipoMovimentacao.Recebimento,
                TipoMovimentacao.Retirada {

    record Aporte() implements TipoMovimentacao {}

    record Desembolso() implements TipoMovimentacao {}

    record Recebimento() implements TipoMovimentacao {}

    record Retirada() implements TipoMovimentacao {}
}
