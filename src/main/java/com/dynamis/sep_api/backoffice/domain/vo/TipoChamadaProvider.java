package com.dynamis.sep_api.backoffice.domain.vo;

/**
 * Categorias de chamada a provider externo elegiveis pra reprocesso manual (Sprint 14 Task 14.4).
 * Cada valor mapeia para um {@code ProviderRetentativaStrategy} no dispatcher.
 */
public enum TipoChamadaProvider {
    KYC,
    KYB,
    PLD,
    OPEN_FINANCE,
    ASSINATURA_DIGITAL,
    PIX_TRANSFERENCIA
}
