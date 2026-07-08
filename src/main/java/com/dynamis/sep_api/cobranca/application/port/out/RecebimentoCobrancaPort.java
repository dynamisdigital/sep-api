package com.dynamis.sep_api.cobranca.application.port.out;

import com.dynamis.sep_api.cobranca.domain.model.Recebimento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida de persistencia de {@link Recebimento} (Sprint 28, ADR 0007). Somente leitura:
 * a escrita de recebimento acontece por cascade da parcela ({@code ParcelaCobrancaPort#salvarEFlush}).
 */
public interface RecebimentoCobrancaPort {

    /** Guard de idempotencia do registro de recebimento (pre e pos lock). */
    Optional<Recebimento> buscarPorIdempotencyKey(String idempotencyKey);

    /** Historico owner-scoped do tomador (Sprint 23): mais recentes primeiro. */
    List<Recebimento> listarPorParcelaOrdenadoPorDataDesc(UUID parcelaId);

    /** Listagem do operador com {@code parcela} ja carregada (fetch join anti-N+1). */
    List<Recebimento> listarTodosComParcelaOrdenadoPorDataDesc();
}
