package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.dto.PropostaCompletaView;
import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.infrastructure.persistence.ParecerCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ScoreInternoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Carrega proposta + score atual + parecer mais recente em uma unica query application-side
 * (Sprint 8 Task 8.5 fix code review). Controller usa este use case em vez de chamar
 * repositorios diretamente — preserva fronteira Hexagonal/DDD (PRD §11).
 */
@Service
public class ConsultarPropostaCompletaUseCase {

    private final PropostaCreditoRepository propostaRepository;
    private final ScoreInternoRepository scoreRepository;
    private final ParecerCreditoRepository parecerRepository;

    public ConsultarPropostaCompletaUseCase(
            PropostaCreditoRepository propostaRepository,
            ScoreInternoRepository scoreRepository,
            ParecerCreditoRepository parecerRepository) {
        this.propostaRepository = propostaRepository;
        this.scoreRepository = scoreRepository;
        this.parecerRepository = parecerRepository;
    }

    @Transactional(readOnly = true)
    public PropostaCompletaView executar(UUID propostaId) {
        PropostaCredito proposta = propostaRepository
                .findById(propostaId)
                .orElseThrow(() -> new PropostaNaoEncontradaException(propostaId));
        return montar(proposta);
    }

    @Transactional(readOnly = true)
    public Stream<PropostaCompletaView> montarBatch(Stream<PropostaCredito> propostas) {
        return propostas.map((Function<PropostaCredito, PropostaCompletaView>) this::montar);
    }

    public PropostaCompletaView montar(PropostaCredito proposta) {
        return new PropostaCompletaView(
                proposta,
                scoreRepository.findByPropostaId(proposta.getId()).orElse(null),
                parecerRepository
                        .findTopByPropostaIdOrderByVersaoDesc(proposta.getId())
                        .orElse(null));
    }
}
