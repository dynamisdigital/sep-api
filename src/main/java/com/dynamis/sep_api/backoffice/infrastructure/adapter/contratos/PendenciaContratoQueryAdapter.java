package com.dynamis.sep_api.backoffice.infrastructure.adapter.contratos;

import com.dynamis.sep_api.backoffice.application.port.out.PendenciaContratoQueryPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ContratoPendenciaView;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/** Adapter de fronteira — delega ao repository de {@code contratos} retornando projecao minima. */
@Component
public class PendenciaContratoQueryAdapter implements PendenciaContratoQueryPort {

    private final ContratoRepository repository;

    public PendenciaContratoQueryAdapter(ContratoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ContratoPendenciaView> contratosAceitosSemAssinatura(OffsetDateTime corte) {
        return repository.findByStatusAndDataModificacaoBefore(StatusFormalizacao.ACEITO, corte).stream()
                .map(c -> new ContratoPendenciaView(c.getId()))
                .toList();
    }
}
