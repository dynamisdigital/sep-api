package com.dynamis.sep_api.cobranca.infrastructure.adapter.credito;

import com.dynamis.sep_api.cobranca.application.port.out.PropostaCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.port.out.PropostaCobrancaView;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que traduz a porta {@link PropostaCobrancaQueryPort} para o repositorio do modulo
 * {@code credito}. Encapsula a unica dependencia inter-modulo de {@code cobranca}; o listener e
 * use case ficam livres de conhecer {@code credito.infrastructure.persistence}.
 */
@Component
public class PropostaCobrancaQueryAdapter implements PropostaCobrancaQueryPort {

    private final PropostaCreditoRepository propostaRepository;

    public PropostaCobrancaQueryAdapter(PropostaCreditoRepository propostaRepository) {
        this.propostaRepository = propostaRepository;
    }

    @Override
    public Optional<PropostaCobrancaView> buscarPorId(UUID propostaId) {
        return propostaRepository.findById(propostaId).map(PropostaCobrancaQueryAdapter::toView);
    }

    private static PropostaCobrancaView toView(PropostaCredito p) {
        return new PropostaCobrancaView(p.getId(), p.getValorSolicitado(), p.getPrazoMeses(), p.getTaxaJurosMensal());
    }
}
