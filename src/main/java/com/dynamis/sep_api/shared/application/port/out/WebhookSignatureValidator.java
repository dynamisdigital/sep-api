package com.dynamis.sep_api.shared.application.port.out;

/**
 * Porta de saida da camada {@code application}: valida a assinatura HMAC do payload de webhook
 * por provider. Adapter padrao em {@code shared.infrastructure.adapter.HmacSignatureValidator}.
 */
public interface WebhookSignatureValidator {

    boolean isValid(String provider, String payload, String signature);
}
