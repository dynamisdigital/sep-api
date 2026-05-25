package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoRecusadaEvent;
import com.dynamis.sep_api.cobranca.domain.exception.CobrancaOwnershipException;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaCobrancaNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoEstadoInvalidoException;
import com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RenegociacaoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tomador recusa renegociacao proposta (Sprint 13 Task 13.6).
 *
 * <p>Pre-condicoes (validadas pelo controller — Task 13.7):
 *
 * <ul>
 *   <li>Tomador autenticado eh owner ({@code Renegociacao.tomadorId}).
 *   <li>Step-up <strong>nao exigido</strong> pra recusa (spec 13.6 — apenas aceite eh sensivel).
 * </ul>
 *
 * <p>Pipeline:
 *
 * <ol>
 *   <li>Carrega renegociacao em {@code PROPOSTA}; rejeita se ja decidida.
 *   <li>Valida ownership.
 *   <li>Re-checa nao-expiracao (mesmo motivo do aceite — race com job de expiracao).
 *   <li>Parcela volta para {@code statusParcelaAnterior} ({@code ATRASADA} ou {@code INADIMPLENTE}).
 *   <li>Marca renegociacao {@code RECUSADA} + publica {@link RenegociacaoRecusadaEvent}.
 * </ol>
 */
@Service
public class RecusarRenegociacaoUseCase {

    private final RenegociacaoRepository renegociacaoRepository;
    private final ParcelaCobrancaRepository parcelaRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RecusarRenegociacaoUseCase(
            RenegociacaoRepository renegociacaoRepository,
            ParcelaCobrancaRepository parcelaRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.renegociacaoRepository = renegociacaoRepository;
        this.parcelaRepository = parcelaRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Renegociacao executar(UUID renegociacaoId, UUID tomadorAutenticadoId) {
        // Hotfix code review Task 13.6: lock pessimista pra serializar aceite/recusa/job expirar.
        Renegociacao renegociacao = renegociacaoRepository
                .findByIdForUpdate(renegociacaoId)
                .orElseThrow(() -> new RenegociacaoNaoEncontradaException(renegociacaoId));
        if (renegociacao.getStatus() != StatusRenegociacao.PROPOSTA) {
            throw new RenegociacaoEstadoInvalidoException(renegociacaoId, renegociacao.getStatus(), "recusar");
        }
        if (!renegociacao.getTomadorId().equals(tomadorAutenticadoId)) {
            throw new CobrancaOwnershipException(renegociacao.getAgendaOriginalId());
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        if (renegociacao.expirouEm(agora)) {
            throw RenegociacaoEstadoInvalidoException.expirada(renegociacaoId);
        }
        UUID parcelaOriginalId = renegociacao.getParcelaOriginalId();
        ParcelaCobranca parcela = parcelaRepository
                .findByIdForUpdate(parcelaOriginalId)
                .orElseThrow(() -> ParcelaCobrancaNaoEncontradaException.porId(parcelaOriginalId));
        parcela.reverterDeNegociacao(renegociacao.getStatusParcelaAnterior());
        parcelaRepository.save(parcela);
        renegociacao.recusar(agora);
        renegociacao = renegociacaoRepository.save(renegociacao);
        eventPublisher.publishEvent(new RenegociacaoRecusadaEvent(
                renegociacao.getId(),
                parcela.getId(),
                renegociacao.getTomadorId(),
                renegociacao.getStatusParcelaAnterior()));
        return renegociacao;
    }
}
