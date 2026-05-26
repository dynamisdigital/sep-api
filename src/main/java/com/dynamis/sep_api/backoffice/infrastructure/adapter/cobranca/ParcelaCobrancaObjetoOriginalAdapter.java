package com.dynamis.sep_api.backoffice.infrastructure.adapter.cobranca;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.application.port.out.ObjetoOriginalQueryPort;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Strategy: resolve {@code PARCELA_COBRANCA} para {@link ObjetoOriginalResumo}. */
@Component
public class ParcelaCobrancaObjetoOriginalAdapter implements ObjetoOriginalQueryPort {

    private final ParcelaCobrancaRepository repository;

    public ParcelaCobrancaObjetoOriginalAdapter(ParcelaCobrancaRepository repository) {
        this.repository = repository;
    }

    @Override
    public TipoEntidadeReferenciada tipoSuportado() {
        return TipoEntidadeReferenciada.PARCELA_COBRANCA;
    }

    @Override
    public Optional<ObjetoOriginalResumo> buscar(UUID entidadeId) {
        return repository.findById(entidadeId)
                .map(p -> new ObjetoOriginalResumo(
                        TipoEntidadeReferenciada.PARCELA_COBRANCA,
                        p.getId(),
                        p.getStatus().name(),
                        "Parcela " + p.getNumero() + " venc " + p.getDataVencimento()));
    }
}
