package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.RecebimentoPixResult;
import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Leitura local de um recebimento Pix para operacao assistida (Sprint 21 Task 21.6). */
@Service
public class ConsultarRecebimentoPixUseCase {

    private final PixRecebimentoRepository repository;

    public ConsultarRecebimentoPixUseCase(PixRecebimentoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public RecebimentoPixResult executar(UUID recebimentoId) {
        PixRecebimento r = repository
                .findById(recebimentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "PIX-404-RECEBIMENTO", "Recebimento Pix nao encontrado: " + recebimentoId));
        return new RecebimentoPixResult(
                r.getId(),
                r.getStatus(),
                r.getValor(),
                r.getEndToEndId(),
                r.getReferenciaId(),
                r.getParcelaId(),
                r.getRecebimentoCobrancaId(),
                r.getMotivoDivergencia(),
                r.getRecebidoEm());
    }
}
