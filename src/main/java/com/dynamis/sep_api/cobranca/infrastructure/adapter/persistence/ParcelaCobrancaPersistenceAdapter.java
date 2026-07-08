package com.dynamis.sep_api.cobranca.infrastructure.adapter.persistence;

import com.dynamis.sep_api.cobranca.application.port.out.ParcelaCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que traduz {@link ParcelaCobrancaPort} para o repository JPA (Sprint 28, ADR 0007).
 * Delegacao pura — queries e locks permanecem no {@link ParcelaCobrancaRepository}.
 */
@Component
public class ParcelaCobrancaPersistenceAdapter implements ParcelaCobrancaPort {

    private final ParcelaCobrancaRepository repository;

    public ParcelaCobrancaPersistenceAdapter(ParcelaCobrancaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ParcelaCobranca> buscarPorId(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<ParcelaCobranca> buscarPorIdComLock(UUID id) {
        return repository.findByIdForUpdate(id);
    }

    @Override
    public boolean existePorId(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public List<ParcelaCobranca> listarPorContratoOrdenadoPorNumero(UUID contratoId) {
        return repository.findByAgenda_ContratoIdOrderByNumeroAsc(contratoId);
    }

    @Override
    public List<ParcelaCobranca> listarTodas() {
        return repository.findAll();
    }

    @Override
    public List<ParcelaCobranca> listarPorStatusOrdenadoPorVencimento(Collection<StatusParcela> statuses) {
        return repository.findByStatusInOrderByDataVencimentoAsc(statuses);
    }

    @Override
    public ParcelaCobranca salvar(ParcelaCobranca parcela) {
        return repository.save(parcela);
    }

    @Override
    public ParcelaCobranca salvarEFlush(ParcelaCobranca parcela) {
        return repository.saveAndFlush(parcela);
    }
}
