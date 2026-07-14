package com.dynamis.sep_api.pix.domain.event;

import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;

import java.util.UUID;

/**
 * Chave Pix da conta operacional removida — transicao efetiva {@code ATIVA -> INATIVA} (Sprint 31).
 * Replay de remocao sobre chave ja INATIVA nao re-publica. Carrega somente ids tecnicos e tipo —
 * nunca valor, hash, mascara, provider id ou idempotency key.
 */
public record PixChaveRemovidaEvent(UUID chaveId, UUID contaEscrowId, TipoChavePix tipo, UUID operadorId) {}
