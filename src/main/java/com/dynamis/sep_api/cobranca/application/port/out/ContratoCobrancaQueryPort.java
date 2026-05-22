package com.dynamis.sep_api.cobranca.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida pra resolver dados minimos do contrato necessarios em cobranca (Sprint 12 Task
 * 12.4 — propostaId pra rotear movimentacao escrow).
 */
public interface ContratoCobrancaQueryPort {

    Optional<UUID> propostaIdDoContrato(UUID contratoId);
}
