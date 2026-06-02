package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.ReferenciaRecebimentoPixResult;
import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixReferenciaRecebimentoRepository;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Leitura local de uma referencia Pix de recebimento (Sprint 21 Task 21.6). */
@Service
public class ConsultarReferenciaRecebimentoPixUseCase {

    private final PixReferenciaRecebimentoRepository repository;

    public ConsultarReferenciaRecebimentoPixUseCase(PixReferenciaRecebimentoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ReferenciaRecebimentoPixResult executar(UUID referenciaId) {
        PixReferenciaRecebimento r = repository
                .findById(referenciaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "PIX-404-REFERENCIA", "Referencia Pix de recebimento nao encontrada: " + referenciaId));
        return new ReferenciaRecebimentoPixResult(
                r.getId(),
                r.getParcelaId(),
                r.getContratoId(),
                r.getTxid(),
                r.getCodigoCopiaCola(),
                r.getValorEsperado(),
                r.getStatus());
    }
}
