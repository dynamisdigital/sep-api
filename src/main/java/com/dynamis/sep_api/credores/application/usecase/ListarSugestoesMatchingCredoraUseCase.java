package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.MatchingCredoraOperacaoView;
import com.dynamis.sep_api.credores.domain.vo.StatusMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.infrastructure.persistence.MatchingCredoraOperacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Listagem operacional das sugestoes de matching pendentes de decisao (Sprint 30 Task 30.3).
 * Retorna somente {@code SUGERIDA} — decididas sao consultadas individualmente ({@code GET /{id}},
 * Task 30.5). Ordenacao deterministica: maior valor elegivel primeiro, empate pela sugestao mais
 * antiga.
 */
@Service
public class ListarSugestoesMatchingCredoraUseCase {

    private final MatchingCredoraOperacaoRepository matchingRepository;

    public ListarSugestoesMatchingCredoraUseCase(MatchingCredoraOperacaoRepository matchingRepository) {
        this.matchingRepository = matchingRepository;
    }

    @Transactional(readOnly = true)
    public List<MatchingCredoraOperacaoView> executar() {
        return matchingRepository
                .findAllByStatusOrderByValorElegivelDescDataCriacaoAsc(StatusMatchingCredoraOperacao.SUGERIDA)
                .stream()
                .map(MatchingCredoraOperacaoView::de)
                .toList();
    }
}
