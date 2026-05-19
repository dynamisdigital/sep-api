package com.dynamis.sep_api.credito.infrastructure.adapter.celcoin.dto;

/**
 * Payload enviado ao endpoint Celcoin Finansystech para criar consentimento Open Finance.
 * Modelagem aproximada — campos exatos dependem da especificacao real da Celcoin/Finansystech
 * (sandbox a confirmar antes de release).
 */
public record CelcoinOpenFinanceConsentRequest(
        String propostaId, String tomadorId, String documento, String redirectUri) {}
