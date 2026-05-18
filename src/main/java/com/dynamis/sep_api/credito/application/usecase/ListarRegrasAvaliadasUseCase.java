package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.RegraCreditoAvaliada;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.RegraCreditoAvaliadaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lista trilha auditavel de regras avaliadas pelo motor pra uma proposta (Sprint 8 Task 8.5 fix
 * code review). Garante que a proposta existe antes de listar — 404 se nao encontrada.
 */
@Service
public class ListarRegrasAvaliadasUseCase {

    private final PropostaCreditoRepository propostaRepository;
    private final RegraCreditoAvaliadaRepository regraRepository;

    public ListarRegrasAvaliadasUseCase(
            PropostaCreditoRepository propostaRepository, RegraCreditoAvaliadaRepository regraRepository) {
        this.propostaRepository = propostaRepository;
        this.regraRepository = regraRepository;
    }

    @Transactional(readOnly = true)
    public List<RegraCreditoAvaliada> executar(UUID propostaId) {
        if (!propostaRepository.existsById(propostaId)) {
            throw new PropostaNaoEncontradaException(propostaId);
        }
        return regraRepository.findByPropostaIdOrderByDataAvaliacaoAsc(propostaId);
    }
}
