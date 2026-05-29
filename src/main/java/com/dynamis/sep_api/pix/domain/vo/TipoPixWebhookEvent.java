package com.dynamis.sep_api.pix.domain.vo;

/**
 * Classificacao de negocio do evento de webhook Pix, derivada do tipo bruto enviado pelo provider
 * (Epic 15 / Sprint 19). A traducao do tipo cru Celcoin para este enum ocorre no adapter, mantendo
 * o dominio livre de strings de provider.
 */
public enum TipoPixWebhookEvent {
    RECEBIMENTO_PIX,
    STATUS_TRANSFERENCIA,
    DESCONHECIDO
}
