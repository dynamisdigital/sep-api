package com.dynamis.sep_api.backoffice.infrastructure.adapter.credito;

import com.dynamis.sep_api.backoffice.application.port.out.PendenciaCreditoQueryPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.PropostaPendenciaView;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/** Adapter de fronteira — delega ao repository de {@code credito} retornando projecao minima. */
@Component
public class PendenciaCreditoQueryAdapter implements PendenciaCreditoQueryPort {

    private final PropostaCreditoRepository repository;

    public PendenciaCreditoQueryAdapter(PropostaCreditoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<PropostaPendenciaView> propostasParadasEmAnalise(OffsetDateTime corte) {
        return repository.findByStatusAndDataModificacaoBefore(StatusProposta.EM_ANALISE, corte).stream()
                .map(p -> new PropostaPendenciaView(p.getId()))
                .toList();
    }
}
