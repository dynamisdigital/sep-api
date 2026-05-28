package com.dynamis.sep_api.credores.application.port.out;

import java.util.UUID;

/**
 * Status contratual de uma operacao da carteira, exposto pelo modulo {@code contratos} ao
 * {@code credores} (Sprint 17). {@code status} como String para nao acoplar {@code credores} ao
 * enum de {@code contratos}.
 */
public record ContratoCarteiraView(UUID contratoId, UUID propostaId, String status) {}
