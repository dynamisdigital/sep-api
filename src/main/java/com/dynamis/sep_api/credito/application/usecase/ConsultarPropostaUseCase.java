package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta uma proposta de credito por id (Sprint 8 Task 8.3). Caller (controller) eh responsavel
 * por aplicar ownership/roles antes de chamar este use case — aqui retornamos a proposta crua.
 */
@Service
public class ConsultarPropostaUseCase {

    private final PropostaCreditoRepository repository;

    public ConsultarPropostaUseCase(PropostaCreditoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PropostaCredito executar(UUID propostaId) {
        return repository.findById(propostaId).orElseThrow(() -> new PropostaNaoEncontradaException(propostaId));
    }

    @Transactional(readOnly = true)
    public PropostaCredito executarComOwnership(UUID propostaId, UUID tomadorId) {
        return repository
                .findByIdAndTomadorId(propostaId, tomadorId)
                .orElseThrow(() -> new PropostaNaoEncontradaException(propostaId));
    }
}
