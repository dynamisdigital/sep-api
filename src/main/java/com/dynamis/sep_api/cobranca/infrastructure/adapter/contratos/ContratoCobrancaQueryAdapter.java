package com.dynamis.sep_api.cobranca.infrastructure.adapter.contratos;

import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ContratoCobrancaQueryAdapter implements ContratoCobrancaQueryPort {

    private static final Logger log = LoggerFactory.getLogger(ContratoCobrancaQueryAdapter.class);

    private final ContratoRepository contratoRepository;

    public ContratoCobrancaQueryAdapter(ContratoRepository contratoRepository) {
        this.contratoRepository = contratoRepository;
    }

    @Override
    public Optional<UUID> propostaIdDoContrato(UUID contratoId) {
        Optional<UUID> propostaId = contratoRepository.findById(contratoId).map(c -> c.getPropostaId());
        if (propostaId.isEmpty()) {
            // Integridade quebrada (CMN 4.656/2018 + LGPD: FK contrato sem CASCADE deveria
            // preservar contrato). Log warn pra alertar antes que use case lance IllegalState.
            log.warn("Contrato {} nao encontrado ao resolver propostaId — possivel integridade quebrada", contratoId);
        }
        return propostaId;
    }

    @Override
    public Optional<UUID> tomadorIdDoContrato(UUID contratoId) {
        return contratoRepository.findById(contratoId).map(c -> c.getTomadorId());
    }

    @Override
    public Map<UUID, UUID> tomadoresPorContratoIds(Collection<UUID> contratoIds) {
        if (contratoIds == null || contratoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return contratoRepository.findAllById(contratoIds).stream()
                .collect(Collectors.toMap(Contrato::getId, Contrato::getTomadorId, (a, b) -> a));
    }
}
