package com.dynamis.sep_api.backoffice.infrastructure.adapter.pix;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.application.port.out.ObjetoOriginalQueryPort;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Strategy (Sprint 21 Task 21.5): resolve {@code PIX_RECEBIMENTO} para {@link ObjetoOriginalResumo} no
 * detalhe do item da fila. Expoe apenas status, valor e parcela vinculada — nunca payload bruto nem
 * chave Pix.
 */
@Component
public class PixRecebimentoObjetoOriginalAdapter implements ObjetoOriginalQueryPort {

    private final PixRecebimentoRepository repository;

    public PixRecebimentoObjetoOriginalAdapter(PixRecebimentoRepository repository) {
        this.repository = repository;
    }

    @Override
    public TipoEntidadeReferenciada tipoSuportado() {
        return TipoEntidadeReferenciada.PIX_RECEBIMENTO;
    }

    @Override
    public Optional<ObjetoOriginalResumo> buscar(UUID entidadeId) {
        return repository
                .findById(entidadeId)
                .map(r -> new ObjetoOriginalResumo(
                        TipoEntidadeReferenciada.PIX_RECEBIMENTO,
                        r.getId(),
                        r.getStatus().name(),
                        "Recebimento Pix valor " + r.getValor()
                                + (r.getParcelaId() != null ? " parcela " + r.getParcelaId() : " sem parcela")));
    }
}
