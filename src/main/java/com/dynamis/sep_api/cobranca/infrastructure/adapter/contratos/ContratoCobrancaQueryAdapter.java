package com.dynamis.sep_api.cobranca.infrastructure.adapter.contratos;

import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ContratoCobrancaQueryAdapter implements ContratoCobrancaQueryPort {

    private final ContratoRepository contratoRepository;

    public ContratoCobrancaQueryAdapter(ContratoRepository contratoRepository) {
        this.contratoRepository = contratoRepository;
    }

    @Override
    public Optional<UUID> propostaIdDoContrato(UUID contratoId) {
        return contratoRepository.findById(contratoId).map(c -> c.getPropostaId());
    }
}
