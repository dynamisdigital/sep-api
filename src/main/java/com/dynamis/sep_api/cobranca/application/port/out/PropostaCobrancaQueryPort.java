package com.dynamis.sep_api.cobranca.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida do modulo {@code cobranca} (Sprint 12 Task 12.3 — fix code review manual) pra
 * consultar dados financeiros da proposta sem depender de {@code credito.infrastructure}.
 *
 * <p>Adapter concreto em {@code cobranca.infrastructure.adapter.credito} traduz pro repositorio
 * de credito; mantem fronteira hexagonal (ADR 0007).
 */
public interface PropostaCobrancaQueryPort {

    Optional<PropostaCobrancaView> buscarPorId(UUID propostaId);
}
