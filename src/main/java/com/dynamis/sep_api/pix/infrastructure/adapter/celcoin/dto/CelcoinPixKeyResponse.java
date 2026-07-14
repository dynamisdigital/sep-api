package com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta da Celcoin ao cadastro de chave Pix (Sprint 31): apenas o identificador tecnico da
 * chave. Contrato skeleton local da Fase 4 — validar contra a documentacao real na Fase 5.
 */
public record CelcoinPixKeyResponse(@JsonProperty("key_id") String keyId) {}
