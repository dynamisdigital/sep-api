package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.IniciarRenegociacaoCommand;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.port.out.ParcelaCobrancaPort;
import com.dynamis.sep_api.cobranca.application.port.out.RenegociacaoCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoPropostaEvent;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaCobrancaNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoConflitanteException;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Propoe nova renegociacao para parcela ATRASADA ou INADIMPLENTE (Sprint 13 Task 13.6).
 *
 * <p>Pre-condicoes (validadas pelo controller — Task 13.7):
 *
 * <ul>
 *   <li>{@code ROLE_FINANCEIRO} ou {@code ROLE_ADMIN}.
 *   <li>{@code @RequireStepUp} cobre operacao sensivel.
 * </ul>
 *
 * <p>Pipeline:
 *
 * <ol>
 *   <li>Lock pessimista da parcela ({@code buscarPorIdComLock}).
 *   <li>Guard de status: parcela em {@code ATRASADA}/{@code INADIMPLENTE}.
 *   <li>Verifica nao haver renegociacao {@code PROPOSTA} ja ativa pra parcela. Defesa em
 *       profundidade: unique parcial {@code uq_renegociacao_parcela_ativa} no DB protege contra
 *       race; convertemos {@link DataIntegrityViolationException} em
 *       {@link RenegociacaoConflitanteException}.
 *   <li>Cria {@link Renegociacao} (status {@code PROPOSTA}, expiracao 7 dias).
 *   <li>Transiciona parcela pra {@code EM_NEGOCIACAO} preservando status anterior.
 *   <li>Publica {@link RenegociacaoPropostaEvent} pra audit (Task 13.8) + listener de notificacao
 *       (segue padrao do ParcelaAtrasouListener — fora desta task).
 * </ol>
 */
@Service
public class IniciarRenegociacaoUseCase {

    /** Janela default de aceite (spec 13.6). */
    public static final Duration JANELA_RENEGOCIACAO = Duration.ofDays(7);

    private final ParcelaCobrancaPort parcelaPort;
    private final RenegociacaoCobrancaPort renegociacaoPort;
    private final ContratoCobrancaQueryPort contratoQuery;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public IniciarRenegociacaoUseCase(
            ParcelaCobrancaPort parcelaPort,
            RenegociacaoCobrancaPort renegociacaoPort,
            ContratoCobrancaQueryPort contratoQuery,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.parcelaPort = parcelaPort;
        this.renegociacaoPort = renegociacaoPort;
        this.contratoQuery = contratoQuery;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Renegociacao executar(IniciarRenegociacaoCommand command) {
        ParcelaCobranca parcela = parcelaPort
                .buscarPorIdComLock(command.parcelaId())
                .orElseThrow(() -> ParcelaCobrancaNaoEncontradaException.porId(command.parcelaId()));
        // Guard duplo: check antes da factory pra mensagem mais clara que o IllegalArgument do dominio.
        if (!parcela.getStatus().permiteIniciarRenegociacao()) {
            throw new com.dynamis.sep_api.cobranca.domain.exception.ParcelaEstadoInvalidoException(
                    "iniciarRenegociacao", parcela.getStatus());
        }
        if (renegociacaoPort.existePorParcelaOriginalEStatus(parcela.getId(), StatusRenegociacao.PROPOSTA)) {
            throw new RenegociacaoConflitanteException(parcela.getId());
        }
        UUID contratoId = parcela.getAgenda().getContratoId();
        UUID tomadorId = contratoQuery
                .tomadorIdDoContrato(contratoId)
                .orElseThrow(() -> new IllegalStateException(
                        "sem tomadorId para contrato " + contratoId + " — dado inconsistente"));
        OffsetDateTime agora = OffsetDateTime.now(clock);
        StatusParcela statusAnterior = parcela.iniciarNegociacao();
        Renegociacao renegociacao = Renegociacao.propor(
                parcela.getId(),
                parcela.getAgenda().getId(),
                tomadorId,
                statusAnterior,
                command.novoValorParcela(),
                command.novoVencimento(),
                command.numeroParcelas(),
                command.desconto(),
                command.justificativa(),
                command.propostaPor(),
                agora,
                agora.plus(JANELA_RENEGOCIACAO));
        try {
            renegociacao = renegociacaoPort.salvarEFlush(renegociacao);
        } catch (DataIntegrityViolationException e) {
            // Race: outra instancia/thread inseriu PROPOSTA entre o guard de existencia e o save.
            // Filtra pelo nome do constraint pra evitar mascarar outras violacoes (FK, check, etc).
            if (mensagemContemConstraint(e, "uq_renegociacao_parcela_ativa")) {
                throw new RenegociacaoConflitanteException(parcela.getId());
            }
            throw e;
        }
        parcelaPort.salvar(parcela);
        eventPublisher.publishEvent(
                new RenegociacaoPropostaEvent(renegociacao.getId(), parcela.getId(), tomadorId, command.propostaPor()));
        return renegociacao;
    }

    /** Walk no causal chain pra detectar nome do constraint na mensagem Hibernate/JDBC. */
    private static boolean mensagemContemConstraint(Throwable erro, String nomeConstraint) {
        Throwable atual = erro;
        while (atual != null) {
            String msg = atual.getMessage();
            if (msg != null && msg.contains(nomeConstraint)) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }
}
