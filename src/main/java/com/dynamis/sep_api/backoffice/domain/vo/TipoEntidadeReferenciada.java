package com.dynamis.sep_api.backoffice.domain.vo;

/**
 * Categoria do objeto de dominio que originou o item da fila operacional (Sprint 14 Task 14.1). O
 * par ({@code tipoEntidade}, {@code entidadeId}) e referencia logica — sem FK no banco, pra evitar
 * acoplamento ao schema dos modulos consumidos.
 */
public enum TipoEntidadeReferenciada {
    ONBOARDING,
    PROPOSTA,
    CONTRATO,
    PARCELA_COBRANCA,
    WEBHOOK_EVENT_LOG,
    PIX_TRANSFERENCIA,
    PIX_RECEBIMENTO,
    OUTRO
}
