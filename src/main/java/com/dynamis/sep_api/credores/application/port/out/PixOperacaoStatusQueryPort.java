package com.dynamis.sep_api.credores.application.port.out;

import com.dynamis.sep_api.credores.application.dto.PixOperacaoStatusView;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta consumer-driven que fornece ao modulo {@code credores} o status Pix publico de um contrato
 * (Sprint 26 — Gate P3), sem acoplar {@code credores} ao dominio de {@code pix}. Retorna vazio quando
 * o contrato nao possui desembolso Pix. Implementada por adapter em
 * {@code credores.infrastructure.adapter.pix}.
 */
public interface PixOperacaoStatusQueryPort {

    Optional<PixOperacaoStatusView> consultarPorContrato(UUID contratoId);
}
