package com.dynamis.sep_api.escrow.application.port.out.dto;

/**
 * Comando para criar uma conta escrow no provedor (Provider Pattern; Epic 15 / Sprint 19). O
 * adapter Celcoin traduz para o formato externo.
 */
public record ComandoCriarContaEscrow(String titular) {}
