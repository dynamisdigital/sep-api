package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.port.out.ParcelaCobrancaPort;
import com.dynamis.sep_api.cobranca.application.port.out.RenegociacaoCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoRecusadaEvent;
import com.dynamis.sep_api.cobranca.domain.exception.CobrancaOwnershipException;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaCobrancaNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoEstadoInvalidoException;
import com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
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
 *   <li>Carrega renegociacao e valida ownership — antes do estado, pra nao-dono nao descobrir
 *       status alheio via 409 (Sprint 27 Task 27.4).
 *   <li>Exige status {@code PROPOSTA}; rejeita se ja decidida.
 *   <li>Re-checa nao-expiracao (mesmo motivo do aceite — race com job de expiracao).
 *   <li>Parcela volta para {@code statusParcelaAnterior} ({@code ATRASADA} ou {@code INADIMPLENTE}).
 *   <li>Marca renegociacao {@code RECUSADA} + publica {@link RenegociacaoRecusadaEvent}.
 * </ol>
 */
@Service
public class RecusarRenegociacaoUseCase {

    private final RenegociacaoCobrancaPort renegociacaoPort;
    private final ParcelaCobrancaPort parcelaPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RecusarRenegociacaoUseCase(
            RenegociacaoCobrancaPort renegociacaoPort,
            ParcelaCobrancaPort parcelaPort,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.renegociacaoPort = renegociacaoPort;
        this.parcelaPort = parcelaPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Renegociacao executar(UUID renegociacaoId, UUID tomadorAutenticadoId) {
        // Hotfix code review Task 13.6: lock pessimista pra serializar aceite/recusa/job expirar.
        Renegociacao renegociacao = renegociacaoPort
                .buscarPorIdComLock(renegociacaoId)
                .orElseThrow(() -> new RenegociacaoNaoEncontradaException(renegociacaoId));
        // Ownership antes do estado (Sprint 27 Task 27.4): nao-dono nao pode descobrir o status
        // da renegociacao via 409; variante neutra nao vaza UUID de agenda alheia.
        if (!renegociacao.getTomadorId().equals(tomadorAutenticadoId)) {
            throw new CobrancaOwnershipException();
        }
        if (renegociacao.getStatus() != StatusRenegociacao.PROPOSTA) {
            throw new RenegociacaoEstadoInvalidoException(renegociacaoId, renegociacao.getStatus(), "recusar");
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        if (renegociacao.expirouEm(agora)) {
            throw RenegociacaoEstadoInvalidoException.expirada(renegociacaoId);
        }
        UUID parcelaOriginalId = renegociacao.getParcelaOriginalId();
        ParcelaCobranca parcela = parcelaPort
                .buscarPorIdComLock(parcelaOriginalId)
                .orElseThrow(() -> ParcelaCobrancaNaoEncontradaException.porId(parcelaOriginalId));
        parcela.reverterDeNegociacao(renegociacao.getStatusParcelaAnterior());
        parcelaPort.salvar(parcela);
        renegociacao.recusar(agora);
        renegociacao = renegociacaoPort.salvar(renegociacao);
        eventPublisher.publishEvent(new RenegociacaoRecusadaEvent(
                renegociacao.getId(),
                parcela.getId(),
                renegociacao.getTomadorId(),
                renegociacao.getStatusParcelaAnterior()));
        return renegociacao;
    }
}
