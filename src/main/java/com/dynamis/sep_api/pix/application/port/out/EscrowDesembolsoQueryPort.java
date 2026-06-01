package com.dynamis.sep_api.pix.application.port.out;

import com.dynamis.sep_api.pix.application.port.out.dto.EscrowDesembolsoView;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida para verificar a wallet/conta escrow operacional da proposta antes do desembolso
 * (Sprint 20 Task 20.1). Implementada por adapter em {@code pix.infrastructure.adapter.escrow}.
 */
public interface EscrowDesembolsoQueryPort {

    Optional<EscrowDesembolsoView> buscarPorProposta(UUID propostaId);
}
