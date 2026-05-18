package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Lista propostas com filtros (status opcional, tomadorId opcional) e paginacao (Sprint 8 Task
 * 8.3). Caller decide se forca ownership (cliente lista proprias) ou se permite filtro livre
 * (financeiro/admin lista qualquer).
 */
@Service
public class ListarPropostasUseCase {

    private final PropostaCreditoRepository repository;

    public ListarPropostasUseCase(PropostaCreditoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<PropostaCredito> listarDoTomador(UUID tomadorId, StatusProposta status, Pageable pageable) {
        if (status == null) {
            return repository.findByTomadorId(tomadorId, pageable);
        }
        return repository.findByStatusInAndTomadorId(java.util.List.of(status), tomadorId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PropostaCredito> listarComFiltros(UUID tomadorId, StatusProposta status, Pageable pageable) {
        if (tomadorId != null && status != null) {
            return repository.findByStatusInAndTomadorId(java.util.List.of(status), tomadorId, pageable);
        }
        if (tomadorId != null) {
            return repository.findByTomadorId(tomadorId, pageable);
        }
        if (status != null) {
            return repository.findByStatus(status, pageable);
        }
        return repository.findAll(pageable);
    }
}
