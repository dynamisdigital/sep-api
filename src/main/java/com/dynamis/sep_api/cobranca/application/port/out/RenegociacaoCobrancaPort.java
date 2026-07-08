package com.dynamis.sep_api.cobranca.application.port.out;

import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida de persistencia de {@link Renegociacao} (Sprint 28, ADR 0007). Queries do job de
 * expiracao seguem no repository JPA.
 */
public interface RenegociacaoCobrancaPort {

    /** Lock pessimista — serializa aceite/recusa/expiracao concorrentes (Sprint 13). */
    Optional<Renegociacao> buscarPorIdComLock(UUID id);

    Optional<Renegociacao> buscarPorParcelaOriginalEStatus(UUID parcelaOriginalId, StatusRenegociacao status);

    boolean existePorParcelaOriginalEStatus(UUID parcelaOriginalId, StatusRenegociacao status);

    Renegociacao salvar(Renegociacao renegociacao);

    /** Persiste com flush imediato — proposta precisa existir antes de mutar a parcela. */
    Renegociacao salvarEFlush(Renegociacao renegociacao);
}
