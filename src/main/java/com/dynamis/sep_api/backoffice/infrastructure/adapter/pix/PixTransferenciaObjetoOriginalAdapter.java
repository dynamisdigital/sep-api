package com.dynamis.sep_api.backoffice.infrastructure.adapter.pix;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.application.port.out.ObjetoOriginalQueryPort;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Strategy (Sprint 20 Task 20.4): resolve {@code PIX_TRANSFERENCIA} para {@link ObjetoOriginalResumo}
 * no detalhe do item da fila. Nunca expoe a chave Pix (apenas mascara/status).
 */
@Component
public class PixTransferenciaObjetoOriginalAdapter implements ObjetoOriginalQueryPort {

    private final PixTransferenciaRepository repository;

    public PixTransferenciaObjetoOriginalAdapter(PixTransferenciaRepository repository) {
        this.repository = repository;
    }

    @Override
    public TipoEntidadeReferenciada tipoSuportado() {
        return TipoEntidadeReferenciada.PIX_TRANSFERENCIA;
    }

    @Override
    public Optional<ObjetoOriginalResumo> buscar(UUID entidadeId) {
        return repository
                .findById(entidadeId)
                .map(t -> new ObjetoOriginalResumo(
                        TipoEntidadeReferenciada.PIX_TRANSFERENCIA,
                        t.getId(),
                        t.getStatus().name(),
                        "Desembolso contrato " + t.getContratoId() + " destino " + t.getChaveDestinoMascara()));
    }
}
