package com.dynamis.sep_api.pix.domain.event;

import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;

import java.util.UUID;

/**
 * Chave Pix da conta operacional cadastrada (Sprint 31). Publicado apenas na criacao nova (replay
 * idempotente nao re-publica). Carrega somente ids tecnicos e tipo — nunca valor, hash, mascara,
 * provider id ou idempotency key.
 */
public record PixChaveCadastradaEvent(UUID chaveId, UUID contaEscrowId, TipoChavePix tipo, UUID operadorId) {}
