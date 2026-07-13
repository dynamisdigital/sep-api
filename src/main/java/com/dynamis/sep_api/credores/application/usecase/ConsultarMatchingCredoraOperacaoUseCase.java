package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.MatchingCredoraOperacaoView;
import com.dynamis.sep_api.credores.domain.exception.MatchingNaoEncontradoException;
import com.dynamis.sep_api.credores.infrastructure.persistence.MatchingCredoraOperacaoRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta individual da sugestao de matching (Sprint 30 Task 30.5). Visao operacional de
 * financeiro/admin (roles na borda) — inexistente responde 404 neutro sem UUID, mesma estrategia
 * anti-enumeracao da decisao.
 */
@Service
public class ConsultarMatchingCredoraOperacaoUseCase {

    private final MatchingCredoraOperacaoRepository matchingRepository;

    public ConsultarMatchingCredoraOperacaoUseCase(MatchingCredoraOperacaoRepository matchingRepository) {
        this.matchingRepository = matchingRepository;
    }

    @Transactional(readOnly = true)
    public MatchingCredoraOperacaoView executar(UUID sugestaoId) {
        if (sugestaoId == null) {
            throw new ValidacaoException("CRD-400-015", "sugestaoId obrigatorio.");
        }
        return matchingRepository
                .findById(sugestaoId)
                .map(MatchingCredoraOperacaoView::de)
                .orElseThrow(MatchingNaoEncontradoException::new);
    }
}
