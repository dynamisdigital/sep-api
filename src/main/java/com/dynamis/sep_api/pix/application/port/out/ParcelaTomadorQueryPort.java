package com.dynamis.sep_api.pix.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida que resolve o tomador dono de uma parcela para a leitura owner-scoped do status Pix
 * da parcela (Sprint 26 — Gate P2), sem acoplar o dominio {@code pix} ao agregado de {@code cobranca}.
 * Retorna vazio quando a parcela nao existe. Implementada por adapter em
 * {@code pix.infrastructure.adapter.cobranca}.
 */
public interface ParcelaTomadorQueryPort {

    Optional<UUID> tomadorIdDaParcela(UUID parcelaId);
}
