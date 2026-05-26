package com.dynamis.sep_api.cobranca.domain.event;

import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;

import java.util.UUID;

/**
 * Disparado quando o tomador recusa uma renegociacao (Sprint 13 Task 13.6). Parcela volta ao
 * {@code statusParcelaRevertido} registrado no inicio da proposta.
 */
public record RenegociacaoRecusadaEvent(
        UUID renegociacaoId, UUID parcelaOriginalId, UUID tomadorId, StatusParcela statusParcelaRevertido) {}
