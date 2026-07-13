package com.dynamis.sep_api.credores.infrastructure.adapter.contratos;

import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.credores.application.port.out.ConsultarContratoParaCarteiraCredoraPort;
import com.dynamis.sep_api.credores.application.port.out.ContratoCarteiraView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapter de leitura do status contratual para a carteira credora (Sprint 17). */
@Component
public class ContratoCarteiraAdapter implements ConsultarContratoParaCarteiraCredoraPort {

    private final ContratoRepository contratoRepository;

    public ContratoCarteiraAdapter(ContratoRepository contratoRepository) {
        this.contratoRepository = contratoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContratoCarteiraView> consultarPorId(UUID contratoId) {
        return contratoRepository
                .findById(contratoId)
                .map(c -> new ContratoCarteiraView(
                        c.getId(), c.getPropostaId(), c.getStatus().name()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContratoCarteiraView> consultarPorIds(Collection<UUID> contratoIds) {
        return contratoRepository.findAllById(contratoIds).stream()
                .map(c -> new ContratoCarteiraView(
                        c.getId(), c.getPropostaId(), c.getStatus().name()))
                .toList();
    }
}
