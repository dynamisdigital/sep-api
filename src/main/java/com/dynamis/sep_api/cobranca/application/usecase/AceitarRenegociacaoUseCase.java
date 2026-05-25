package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoAceitaEvent;
import com.dynamis.sep_api.cobranca.domain.exception.CobrancaOwnershipException;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaCobrancaNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoEstadoInvalidoException;
import com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.AgendaPagamentoRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RenegociacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tomador aceita renegociacao proposta (Sprint 13 Task 13.6).
 *
 * <p>Pre-condicoes (validadas pelo controller — Task 13.7):
 *
 * <ul>
 *   <li>Tomador autenticado eh owner ({@code Renegociacao.tomadorId}).
 *   <li>{@code @RequireStepUp} cobre operacao sensivel.
 * </ul>
 *
 * <p>Pipeline:
 *
 * <ol>
 *   <li>Carrega {@link Renegociacao} em status {@code PROPOSTA} (rejeita se ja decidida ou expirada).
 *   <li>Valida ownership via {@code tomadorId}.
 *   <li>Verifica nao-expiracao na hora ({@code Renegociacao.expirouEm}). Defesa: job
 *       {@code ExpirarRenegociacaoJob} cobre expiracao em background, mas use case re-checa
 *       pra evitar aceite em proposta vencida entre boot e execucao do job.
 *   <li>Marca agenda original como substituida (UNIQUE parcial libera o slot ativo).
 *   <li>Gera nova {@link AgendaPagamento} substituta com {@code numeroParcelas} novas parcelas
 *       (composicao principal apenas — juros/multa/encargos zerados; sao recalculados via
 *       calculadoras da Sprint 12 quando a primeira atrasar).
 *   <li>Transiciona parcela original pra {@code RENEGOCIADA} (final).
 *   <li>Persiste renegociacao decidida + publica {@link RenegociacaoAceitaEvent}.
 * </ol>
 */
@Service
public class AceitarRenegociacaoUseCase {

    private final RenegociacaoRepository renegociacaoRepository;
    private final ParcelaCobrancaRepository parcelaRepository;
    private final AgendaPagamentoRepository agendaRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AceitarRenegociacaoUseCase(
            RenegociacaoRepository renegociacaoRepository,
            ParcelaCobrancaRepository parcelaRepository,
            AgendaPagamentoRepository agendaRepository,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.renegociacaoRepository = renegociacaoRepository;
        this.parcelaRepository = parcelaRepository;
        this.agendaRepository = agendaRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Renegociacao executar(UUID renegociacaoId, UUID tomadorAutenticadoId) {
        // Hotfix code review Task 13.6: lock pessimista na renegociacao serializa transicoes
        // concorrentes (aceite/recusa/job expirar). Sem isso, job poderia expirar entre o
        // status check e o accept call, deixando renegociacao em estado inconsistente.
        Renegociacao renegociacao = renegociacaoRepository
                .findByIdForUpdate(renegociacaoId)
                .orElseThrow(() -> new RenegociacaoNaoEncontradaException(renegociacaoId));
        if (renegociacao.getStatus() != StatusRenegociacao.PROPOSTA) {
            throw new RenegociacaoEstadoInvalidoException(renegociacaoId, renegociacao.getStatus(), "aceitar");
        }
        if (!renegociacao.getTomadorId().equals(tomadorAutenticadoId)) {
            throw new CobrancaOwnershipException(renegociacao.getAgendaOriginalId());
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        if (renegociacao.expirouEm(agora)) {
            throw RenegociacaoEstadoInvalidoException.expirada(renegociacaoId);
        }
        UUID parcelaOriginalId = renegociacao.getParcelaOriginalId();
        ParcelaCobranca parcelaOriginal = parcelaRepository
                .findByIdForUpdate(parcelaOriginalId)
                .orElseThrow(() -> ParcelaCobrancaNaoEncontradaException.porId(parcelaOriginalId));
        AgendaPagamento agendaOriginal = parcelaOriginal.getAgenda();
        agendaOriginal.marcarSubstituida();
        // Flush antes do save da substituta — UNIQUE parcial em (contrato_id, ativa=true) so libera
        // depois que o update commitar o ativa=false.
        agendaRepository.saveAndFlush(agendaOriginal);

        AgendaPagamento novaAgenda = AgendaPagamento.criarSubstituta(
                agendaOriginal.getContratoId(), agendaOriginal.getId(), planejarParcelas(renegociacao));
        novaAgenda = agendaRepository.save(novaAgenda);

        parcelaOriginal.marcarRenegociada();
        parcelaRepository.save(parcelaOriginal);

        renegociacao.aceitar(novaAgenda.getId(), agora);
        renegociacao = renegociacaoRepository.save(renegociacao);

        eventPublisher.publishEvent(new RenegociacaoAceitaEvent(
                renegociacao.getId(),
                parcelaOriginal.getId(),
                agendaOriginal.getId(),
                novaAgenda.getId(),
                renegociacao.getTomadorId()));
        return renegociacao;
    }

    /**
     * Gera lista de {@link ParcelaPlanejada} a partir das novas condicoes da renegociacao —
     * composicao apenas com principal (juros/multa/encargos zeram nesta agenda substituta;
     * recalculo quando atrasar usa calculadoras da Sprint 12). Vencimentos mensais a partir
     * de {@code novoVencimento}.
     */
    private static List<ParcelaPlanejada> planejarParcelas(Renegociacao renegociacao) {
        List<ParcelaPlanejada> planejadas = new ArrayList<>(renegociacao.getNumeroParcelas());
        LocalDate vencimento = renegociacao.getNovoVencimento();
        for (int i = 1; i <= renegociacao.getNumeroParcelas(); i++) {
            planejadas.add(new ParcelaPlanejada(
                    i, ComposicaoValor.principalApenas(renegociacao.getNovoValorParcela()), vencimento));
            vencimento = vencimento.plusMonths(1);
        }
        return planejadas;
    }
}
