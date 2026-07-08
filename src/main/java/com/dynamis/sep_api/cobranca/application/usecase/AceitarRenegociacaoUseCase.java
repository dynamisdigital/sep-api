package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.port.out.AgendaPagamentoCobrancaPort;
import com.dynamis.sep_api.cobranca.application.port.out.ParcelaCobrancaPort;
import com.dynamis.sep_api.cobranca.application.port.out.RenegociacaoCobrancaPort;
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
 *   <li>{@code @RequireStepUpEstrito} cobre operacao sensivel (Sprint 27 — MFA ativo, sem bypass).
 * </ul>
 *
 * <p>Pipeline:
 *
 * <ol>
 *   <li>Carrega {@link Renegociacao} e valida ownership via {@code tomadorId} — antes do estado,
 *       pra nao-dono nao descobrir status alheio via 409 (Sprint 27 Task 27.4).
 *   <li>Exige status {@code PROPOSTA} (rejeita se ja decidida ou expirada).
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

    private final RenegociacaoCobrancaPort renegociacaoPort;
    private final ParcelaCobrancaPort parcelaPort;
    private final AgendaPagamentoCobrancaPort agendaPort;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AceitarRenegociacaoUseCase(
            RenegociacaoCobrancaPort renegociacaoPort,
            ParcelaCobrancaPort parcelaPort,
            AgendaPagamentoCobrancaPort agendaPort,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.renegociacaoPort = renegociacaoPort;
        this.parcelaPort = parcelaPort;
        this.agendaPort = agendaPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Renegociacao executar(UUID renegociacaoId, UUID tomadorAutenticadoId) {
        // Hotfix code review Task 13.6: lock pessimista na renegociacao serializa transicoes
        // concorrentes (aceite/recusa/job expirar). Sem isso, job poderia expirar entre o
        // status check e o accept call, deixando renegociacao em estado inconsistente.
        Renegociacao renegociacao = renegociacaoPort
                .buscarPorIdComLock(renegociacaoId)
                .orElseThrow(() -> new RenegociacaoNaoEncontradaException(renegociacaoId));
        // Ownership antes do estado (Sprint 27 Task 27.4): nao-dono nao pode descobrir o status
        // da renegociacao via 409; variante neutra nao vaza UUID de agenda alheia.
        if (!renegociacao.getTomadorId().equals(tomadorAutenticadoId)) {
            throw new CobrancaOwnershipException();
        }
        if (renegociacao.getStatus() != StatusRenegociacao.PROPOSTA) {
            throw new RenegociacaoEstadoInvalidoException(renegociacaoId, renegociacao.getStatus(), "aceitar");
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        if (renegociacao.expirouEm(agora)) {
            throw RenegociacaoEstadoInvalidoException.expirada(renegociacaoId);
        }
        UUID parcelaOriginalId = renegociacao.getParcelaOriginalId();
        ParcelaCobranca parcelaOriginal = parcelaPort
                .buscarPorIdComLock(parcelaOriginalId)
                .orElseThrow(() -> ParcelaCobrancaNaoEncontradaException.porId(parcelaOriginalId));
        AgendaPagamento agendaOriginal = parcelaOriginal.getAgenda();
        agendaOriginal.marcarSubstituida();
        // Flush antes do save da substituta — UNIQUE parcial em (contrato_id, ativa=true) so libera
        // depois que o update commitar o ativa=false.
        agendaPort.salvarEFlush(agendaOriginal);

        AgendaPagamento novaAgenda = AgendaPagamento.criarSubstituta(
                agendaOriginal.getContratoId(), agendaOriginal.getId(), planejarParcelas(renegociacao));
        novaAgenda = agendaPort.salvar(novaAgenda);

        parcelaOriginal.marcarRenegociada();
        parcelaPort.salvar(parcelaOriginal);

        renegociacao.aceitar(novaAgenda.getId(), agora);
        renegociacao = renegociacaoPort.salvar(renegociacao);

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
