package com.dynamis.sep_api.shared.domain.model;

/**
 * Estados possiveis de um {@link WebhookEventLog}. Sprint 4 grava apenas {@link #PENDENTE}; os
 * demais ficam reservados para o processamento assincrono de Epics futuras (Pix, KYC).
 */
public enum WebhookEventStatus {
    PENDENTE,
    PROCESSADO,
    FALHOU
}
