package com.dynamis.sep_api.credito.application.dto;

/**
 * Command derivado do callback Celcoin Open Finance (Sprint 9 Task 9.3). Discrimina autorizacao vs
 * negacao via {@link #autorizado()}. Idempotencia/HMAC ja foram validados no webhook controller
 * (Task 9.5).
 */
public record ProcessarCallbackConsentimentoCommand(String idExternoCelcoin, boolean autorizado) {}
