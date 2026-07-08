package com.dynamis.sep_api.cobranca.infrastructure.adapter.persistence;

import com.dynamis.sep_api.cobranca.application.port.out.AgendaPagamentoCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.AgendaPagamentoRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que traduz {@link AgendaPagamentoCobrancaPort} para o repository JPA (Sprint 28,
 * ADR 0007). Delegacao pura; a {@code DataIntegrityViolationException} da UNIQUE parcial atravessa
 * inalterada — o tratamento de race continua no use case de geracao.
 */
@Component
public class AgendaPagamentoPersistenceAdapter implements AgendaPagamentoCobrancaPort {

    private final AgendaPagamentoRepository repository;

    public AgendaPagamentoPersistenceAdapter(AgendaPagamentoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AgendaPagamento> buscarAtivaPorContrato(UUID contratoId) {
        return repository.findByContratoIdAndAtivaTrue(contratoId);
    }

    @Override
    public AgendaPagamento salvar(AgendaPagamento agenda) {
        return repository.save(agenda);
    }

    @Override
    public AgendaPagamento salvarEFlush(AgendaPagamento agenda) {
        return repository.saveAndFlush(agenda);
    }
}
