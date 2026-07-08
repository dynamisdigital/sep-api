package com.dynamis.sep_api.cobranca.infrastructure.adapter.persistence;

import com.dynamis.sep_api.cobranca.application.port.out.RenegociacaoCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RenegociacaoRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que traduz {@link RenegociacaoCobrancaPort} para o repository JPA (Sprint 28, ADR 0007).
 * Delegacao pura — o lock pessimista permanece na query do repository.
 */
@Component
public class RenegociacaoPersistenceAdapter implements RenegociacaoCobrancaPort {

    private final RenegociacaoRepository repository;

    public RenegociacaoPersistenceAdapter(RenegociacaoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Renegociacao> buscarPorIdComLock(UUID id) {
        return repository.findByIdForUpdate(id);
    }

    @Override
    public Optional<Renegociacao> buscarPorParcelaOriginalEStatus(UUID parcelaOriginalId, StatusRenegociacao status) {
        return repository.findByParcelaOriginalIdAndStatus(parcelaOriginalId, status);
    }

    @Override
    public boolean existePorParcelaOriginalEStatus(UUID parcelaOriginalId, StatusRenegociacao status) {
        return repository.existsByParcelaOriginalIdAndStatus(parcelaOriginalId, status);
    }

    @Override
    public Renegociacao salvar(Renegociacao renegociacao) {
        return repository.save(renegociacao);
    }

    @Override
    public Renegociacao salvarEFlush(Renegociacao renegociacao) {
        return repository.saveAndFlush(renegociacao);
    }
}
