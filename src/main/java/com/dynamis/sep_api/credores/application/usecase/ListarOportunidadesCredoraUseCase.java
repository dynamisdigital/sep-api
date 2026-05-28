package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.OportunidadeView;
import com.dynamis.sep_api.credores.domain.exception.EmpresaCredoraNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.vo.StatusOportunidade;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OportunidadeInvestimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lista as oportunidades disponiveis para a credora do usuario autenticado (Sprint 17, Task 17.4).
 * Exige que o usuario possua uma credora; nao exige elegibilidade (so a manifestacao de interesse
 * exige). Sprint 17 nao faz matching — o pool de oportunidades e o mesmo para todas as credoras.
 */
@Service
public class ListarOportunidadesCredoraUseCase {

    private final EmpresaCredoraRepository empresaRepository;
    private final OportunidadeInvestimentoRepository oportunidadeRepository;

    public ListarOportunidadesCredoraUseCase(
            EmpresaCredoraRepository empresaRepository, OportunidadeInvestimentoRepository oportunidadeRepository) {
        this.empresaRepository = empresaRepository;
        this.oportunidadeRepository = oportunidadeRepository;
    }

    @Transactional(readOnly = true)
    public List<OportunidadeView> executar(UUID usuarioId) {
        empresaRepository
                .findByUsuarioId(usuarioId)
                .orElseThrow(() -> EmpresaCredoraNaoEncontradaException.porUsuario(usuarioId));
        return oportunidadeRepository.findByStatusOrderByDataCriacaoDesc(StatusOportunidade.DISPONIVEL).stream()
                .map(OportunidadeView::de)
                .toList();
    }
}
