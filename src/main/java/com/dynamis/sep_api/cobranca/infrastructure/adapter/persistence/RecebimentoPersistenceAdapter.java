package com.dynamis.sep_api.cobranca.infrastructure.adapter.persistence;

import com.dynamis.sep_api.cobranca.application.port.out.RecebimentoCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.model.Recebimento;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RecebimentoRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que traduz {@link RecebimentoCobrancaPort} para o repository JPA (Sprint 28, ADR 0007).
 * Delegacao pura — o fetch join anti-N+1 permanece na query do repository.
 */
@Component
public class RecebimentoPersistenceAdapter implements RecebimentoCobrancaPort {

    private final RecebimentoRepository repository;

    public RecebimentoPersistenceAdapter(RecebimentoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Recebimento> buscarPorIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public List<Recebimento> listarPorParcelaOrdenadoPorDataDesc(UUID parcelaId) {
        return repository.findByParcela_IdOrderByDataRecebimentoDesc(parcelaId);
    }

    @Override
    public List<Recebimento> listarTodosComParcelaOrdenadoPorDataDesc() {
        return repository.findAllWithParcelaOrderByDataDesc();
    }
}
