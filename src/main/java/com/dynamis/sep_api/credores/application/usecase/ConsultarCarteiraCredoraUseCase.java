package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.OperacaoCarteiraView;
import com.dynamis.sep_api.credores.application.service.OperacaoCarteiraEnricher;
import com.dynamis.sep_api.credores.domain.exception.EmpresaCredoraNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Lista a carteira (operacoes financiadas) da credora do usuario autenticado (Sprint 17). */
@Service
public class ConsultarCarteiraCredoraUseCase {

    private final EmpresaCredoraRepository empresaRepository;
    private final OperacaoFinanciadaRepository operacaoRepository;
    private final OperacaoCarteiraEnricher enricher;

    public ConsultarCarteiraCredoraUseCase(
            EmpresaCredoraRepository empresaRepository,
            OperacaoFinanciadaRepository operacaoRepository,
            OperacaoCarteiraEnricher enricher) {
        this.empresaRepository = empresaRepository;
        this.operacaoRepository = operacaoRepository;
        this.enricher = enricher;
    }

    @Transactional(readOnly = true)
    public List<OperacaoCarteiraView> executar(UUID usuarioId) {
        EmpresaCredora credora = empresaRepository
                .findByUsuarioId(usuarioId)
                .orElseThrow(() -> EmpresaCredoraNaoEncontradaException.porUsuario(usuarioId));
        return operacaoRepository.findByEmpresaCredoraIdOrderByDataCriacaoDesc(credora.getId()).stream()
                .map(enricher::enriquecer)
                .toList();
    }
}
