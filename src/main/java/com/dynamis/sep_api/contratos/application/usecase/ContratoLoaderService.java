package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.domain.exception.ContratoNaoEncontradoException;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Helper compartilhado pra carregar {@link Contrato} com lock pessimista (Sprint 11). Centraliza
 * a regra de NotFound entre os use cases de assinatura digital.
 */
@Component
public class ContratoLoaderService {

    private final ContratoRepository contratoRepository;

    public ContratoLoaderService(ContratoRepository contratoRepository) {
        this.contratoRepository = contratoRepository;
    }

    public Contrato carregarComLock(UUID contratoId) {
        return contratoRepository
                .findByIdForUpdate(contratoId)
                .orElseThrow(() -> ContratoNaoEncontradoException.porId(contratoId));
    }

    public Contrato carregar(UUID contratoId) {
        return contratoRepository
                .findById(contratoId)
                .orElseThrow(() -> ContratoNaoEncontradoException.porId(contratoId));
    }
}
