package com.dynamis.sep_api.backoffice.domain.vo;

/**
 * Categorias de item da fila operacional (Sprint 14 Task 14.1).
 *
 * <p>Cada listener da Task 14.2 produz exatamente um destes tipos. O par
 * ({@code tipo}, {@code tipoEntidade}, {@code entidadeId}) garante idempotencia via UNIQUE parcial
 * em {@code item_fila_operacional} (V33).
 */
public enum TipoItemFila {
    ONBOARDING_PENDENTE,
    ONBOARDING_ERRO,
    PROPOSTA_PENDENTE,
    CONTRATO_NAO_ASSINADO,
    COBRANCA_INADIMPLENTE,
    WEBHOOK_FALHOU,
    DESEMBOLSO_PIX_FALHOU,
    OUTRO
}
