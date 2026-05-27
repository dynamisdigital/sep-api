package com.dynamis.sep_api.cobranca.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida pra resolver dados minimos do contrato necessarios em cobranca (Sprint 12 Task
 * 12.4 — propostaId pra rotear movimentacao escrow).
 *
 * <p>Sprint 15 Task 15.4 (15F-002): {@link #tomadoresPorContratoIds(Collection)} permite resolver
 * em lote pra evitar N+1 em listagens (ex.: inadimplencia).
 */
public interface ContratoCobrancaQueryPort {

    Optional<UUID> propostaIdDoContrato(UUID contratoId);

    Optional<UUID> tomadorIdDoContrato(UUID contratoId);

    /**
     * Resolve {@code tomadorId} para um conjunto de {@code contratoIds} numa unica query. Retorna
     * {@code Map<contratoId, tomadorId>}. Contratos inexistentes ficam fora do map.
     */
    Map<UUID, UUID> tomadoresPorContratoIds(Collection<UUID> contratoIds);
}
