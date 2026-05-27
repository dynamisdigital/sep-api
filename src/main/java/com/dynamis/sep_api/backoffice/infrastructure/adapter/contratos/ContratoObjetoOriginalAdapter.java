package com.dynamis.sep_api.backoffice.infrastructure.adapter.contratos;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.application.port.out.ObjetoOriginalQueryPort;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Strategy: resolve {@code CONTRATO} para {@link ObjetoOriginalResumo}. */
@Component
public class ContratoObjetoOriginalAdapter implements ObjetoOriginalQueryPort {

    private final ContratoRepository repository;

    public ContratoObjetoOriginalAdapter(ContratoRepository repository) {
        this.repository = repository;
    }

    @Override
    public TipoEntidadeReferenciada tipoSuportado() {
        return TipoEntidadeReferenciada.CONTRATO;
    }

    @Override
    public Optional<ObjetoOriginalResumo> buscar(UUID entidadeId) {
        return repository
                .findById(entidadeId)
                .map(c -> new ObjetoOriginalResumo(
                        TipoEntidadeReferenciada.CONTRATO,
                        c.getId(),
                        c.getStatus().name(),
                        "Contrato da proposta " + c.getPropostaId()));
    }
}
