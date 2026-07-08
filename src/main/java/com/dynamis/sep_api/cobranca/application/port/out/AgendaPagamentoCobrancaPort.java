package com.dynamis.sep_api.cobranca.application.port.out;

import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida de persistencia de {@link AgendaPagamento} (Sprint 28, ADR 0007). Cobre geracao
 * idempotente de agenda e substituicao por renegociacao aceita.
 */
public interface AgendaPagamentoCobrancaPort {

    Optional<AgendaPagamento> buscarAtivaPorContrato(UUID contratoId);

    AgendaPagamento salvar(AgendaPagamento agenda);

    /**
     * Persiste com flush imediato. A UNIQUE parcial em {@code (contrato_id, ativa=true)} depende do
     * flush: a geracao usa a violacao pra resolver race de dupla criacao, e o aceite de renegociacao
     * precisa consolidar {@code ativa=false} da agenda original antes de salvar a substituta.
     */
    AgendaPagamento salvarEFlush(AgendaPagamento agenda);
}
