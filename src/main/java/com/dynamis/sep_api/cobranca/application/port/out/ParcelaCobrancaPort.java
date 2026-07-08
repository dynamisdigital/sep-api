package com.dynamis.sep_api.cobranca.application.port.out;

import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida de persistencia de {@link ParcelaCobranca} (Sprint 28, ADR 0007). Espelha somente
 * as operacoes consumidas pelos use cases de {@code cobranca}; queries de jobs/listeners seguem no
 * repository JPA de {@code infrastructure.persistence}.
 */
public interface ParcelaCobrancaPort {

    Optional<ParcelaCobranca> buscarPorId(UUID id);

    /** Lock pessimista — serializa recebimento/renegociacao concorrentes sobre a mesma parcela. */
    Optional<ParcelaCobranca> buscarPorIdComLock(UUID id);

    boolean existePorId(UUID id);

    List<ParcelaCobranca> listarPorContratoOrdenadoPorNumero(UUID contratoId);

    /** Listagem completa do operador (FINANCEIRO/ADMIN sem filtro de contrato). */
    List<ParcelaCobranca> listarTodas();

    List<ParcelaCobranca> listarPorStatusOrdenadoPorVencimento(Collection<StatusParcela> statuses);

    ParcelaCobranca salvar(ParcelaCobranca parcela);

    /** Persiste com flush imediato — recebimento precisa do estado consolidado dentro do lock. */
    ParcelaCobranca salvarEFlush(ParcelaCobranca parcela);
}
