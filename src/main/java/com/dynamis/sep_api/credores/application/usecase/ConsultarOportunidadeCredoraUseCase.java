package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.OportunidadeView;
import com.dynamis.sep_api.credores.domain.exception.EmpresaCredoraNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.exception.OportunidadeNaoEncontradaException;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OportunidadeInvestimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta o detalhe de uma oportunidade para a credora do usuario autenticado (Sprint 17, Task
 * 17.5). Exige que o usuario possua credora; oportunidade visivel independe de status.
 */
@Service
public class ConsultarOportunidadeCredoraUseCase {

    private final EmpresaCredoraRepository empresaRepository;
    private final OportunidadeInvestimentoRepository oportunidadeRepository;

    public ConsultarOportunidadeCredoraUseCase(
            EmpresaCredoraRepository empresaRepository, OportunidadeInvestimentoRepository oportunidadeRepository) {
        this.empresaRepository = empresaRepository;
        this.oportunidadeRepository = oportunidadeRepository;
    }

    @Transactional(readOnly = true)
    public OportunidadeView executar(UUID usuarioId, UUID oportunidadeId) {
        empresaRepository
                .findByUsuarioId(usuarioId)
                .orElseThrow(() -> EmpresaCredoraNaoEncontradaException.porUsuario(usuarioId));
        return oportunidadeRepository
                .findById(oportunidadeId)
                .map(OportunidadeView::de)
                .orElseThrow(() -> new OportunidadeNaoEncontradaException(oportunidadeId));
    }
}
