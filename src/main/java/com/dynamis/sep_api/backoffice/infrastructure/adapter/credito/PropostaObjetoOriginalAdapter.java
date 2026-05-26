package com.dynamis.sep_api.backoffice.infrastructure.adapter.credito;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.application.port.out.ObjetoOriginalQueryPort;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Strategy: resolve {@code PROPOSTA} para {@link ObjetoOriginalResumo}. */
@Component
public class PropostaObjetoOriginalAdapter implements ObjetoOriginalQueryPort {

    private final PropostaCreditoRepository repository;

    public PropostaObjetoOriginalAdapter(PropostaCreditoRepository repository) {
        this.repository = repository;
    }

    @Override
    public TipoEntidadeReferenciada tipoSuportado() {
        return TipoEntidadeReferenciada.PROPOSTA;
    }

    @Override
    public Optional<ObjetoOriginalResumo> buscar(UUID entidadeId) {
        return repository.findById(entidadeId)
                .map(p -> new ObjetoOriginalResumo(
                        TipoEntidadeReferenciada.PROPOSTA,
                        p.getId(),
                        p.getStatus().name(),
                        "Proposta " + p.getTipoOperacao().name() + " valor R$ " + p.getValorSolicitado()));
    }
}
