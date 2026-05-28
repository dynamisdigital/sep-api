package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.OperacaoCarteiraView;
import com.dynamis.sep_api.credores.application.service.OperacaoCarteiraEnricher;
import com.dynamis.sep_api.credores.domain.exception.EmpresaCredoraNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.exception.OperacaoFinanciadaNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta o detalhe de uma operacao da carteira da credora do usuario autenticado (Sprint 17).
 * Ownership garantido por busca combinada id + empresaCredoraId.
 */
@Service
public class ConsultarOperacaoCarteiraUseCase {

    private final EmpresaCredoraRepository empresaRepository;
    private final OperacaoFinanciadaRepository operacaoRepository;
    private final OperacaoCarteiraEnricher enricher;

    public ConsultarOperacaoCarteiraUseCase(
            EmpresaCredoraRepository empresaRepository,
            OperacaoFinanciadaRepository operacaoRepository,
            OperacaoCarteiraEnricher enricher) {
        this.empresaRepository = empresaRepository;
        this.operacaoRepository = operacaoRepository;
        this.enricher = enricher;
    }

    @Transactional(readOnly = true)
    public OperacaoCarteiraView executar(UUID usuarioId, UUID operacaoId) {
        EmpresaCredora credora = empresaRepository
                .findByUsuarioId(usuarioId)
                .orElseThrow(() -> EmpresaCredoraNaoEncontradaException.porUsuario(usuarioId));
        OperacaoFinanciada operacao = operacaoRepository
                .findByIdAndEmpresaCredoraId(operacaoId, credora.getId())
                .orElseThrow(() -> new OperacaoFinanciadaNaoEncontradaException(operacaoId));
        return enricher.enriquecer(operacao);
    }
}
