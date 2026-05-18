package com.dynamis.sep_api.credito.application.service;

import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import com.dynamis.sep_api.credito.application.service.dto.ResultadoAvaliacaoCredito;
import com.dynamis.sep_api.credito.domain.event.PropostaAvaliadaPeloMotorEvent;
import com.dynamis.sep_api.credito.domain.event.PropostaRejeitadaEvent;
import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.DecisaoCredito;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.model.RegraCreditoAvaliada;
import com.dynamis.sep_api.credito.domain.model.ScoreInterno;
import com.dynamis.sep_api.credito.domain.vo.OrigemDecisao;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.infrastructure.persistence.DecisaoCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.RegraCreditoAvaliadaRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ScoreInternoRepository;
import com.dynamis.sep_api.onboarding.application.query.ConsultarOnboardingParaCreditoQuery;
import com.dynamis.sep_api.onboarding.application.query.OnboardingResumoCredito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Operacoes transacionais isoladas do {@code AvaliarPropostaUseCase} (Sprint 8 Task 8.3).
 *
 * <p>Cada metodo abre uma transacao propria com {@code REQUIRES_NEW} pra que o {@code
 * AvaliarPropostaUseCase} (sem {@code @Transactional} na entry) possa orquestrar happy path e
 * fallback de {@link StatusProposta#PENDENCIA} em transacoes distintas — caso a transacao do
 * happy path entre em estado {@code rollback-only}, o fallback ainda consegue persistir o status.
 *
 * <p>Por que dois metodos em vez de um? Self-injection com {@code @Lazy} funcionaria mas adiciona
 * acoplamento sutil; separar em um service auxiliar deixa a estrutura mais legivel e testavel.
 */
@Service
public class PropostaAvaliacaoTransacional {

    private final PropostaCreditoRepository propostaRepository;
    private final ScoreInternoRepository scoreRepository;
    private final RegraCreditoAvaliadaRepository regraRepository;
    private final DecisaoCreditoRepository decisaoRepository;
    private final ConsultarOnboardingParaCreditoQuery onboardingQuery;
    private final MotorRegrasCredito motor;
    private final ApplicationEventPublisher eventPublisher;

    public PropostaAvaliacaoTransacional(
            PropostaCreditoRepository propostaRepository,
            ScoreInternoRepository scoreRepository,
            RegraCreditoAvaliadaRepository regraRepository,
            DecisaoCreditoRepository decisaoRepository,
            ConsultarOnboardingParaCreditoQuery onboardingQuery,
            MotorRegrasCredito motor,
            ApplicationEventPublisher eventPublisher) {
        this.propostaRepository = propostaRepository;
        this.scoreRepository = scoreRepository;
        this.regraRepository = regraRepository;
        this.decisaoRepository = decisaoRepository;
        this.onboardingQuery = onboardingQuery;
        this.motor = motor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Happy path: carrega proposta + onboarding, executa motor, persiste trilha (score + regras),
     * aplica sugestao no agregado. Em rejeicao automatica, grava {@link DecisaoCredito} com origem
     * {@link OrigemDecisao#MOTOR} e publica {@link PropostaRejeitadaEvent}. Sempre publica {@link
     * PropostaAvaliadaPeloMotorEvent}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoAvaliacaoCredito avaliar(UUID propostaId) {
        PropostaCredito proposta = propostaRepository
                .findById(propostaId)
                .orElseThrow(() -> new PropostaNaoEncontradaException(propostaId));

        OnboardingResumoCredito resumo = onboardingQuery
                .consultarPorId(proposta.getSolicitacaoOnboardingId())
                .orElseThrow(() -> new PropostaNaoEncontradaException(propostaId));

        ContextoAvaliacaoCredito contexto = new ContextoAvaliacaoCredito(
                proposta, resumo.tipoSolicitante(), resumo.status(), resumo.dataNascimento(), resumo.dataAbertura());

        ResultadoAvaliacaoCredito resultado = motor.avaliar(contexto);
        persistirTrilha(proposta, resultado);
        proposta.aplicarSugestaoMotor(resultado.statusSugerido());
        propostaRepository.save(proposta);

        if (resultado.statusSugerido() == StatusProposta.REJEITADA) {
            decisaoRepository.save(
                    DecisaoCredito.porMotor(proposta.getId(), StatusProposta.REJEITADA, resultado.score()));
            eventPublisher.publishEvent(
                    new PropostaRejeitadaEvent(proposta.getId(), proposta.getTomadorId(), OrigemDecisao.MOTOR, null));
        }

        eventPublisher.publishEvent(new PropostaAvaliadaPeloMotorEvent(
                proposta.getId(),
                proposta.getTomadorId(),
                resultado.score(),
                resultado.statusSugerido(),
                resultado.falhas(),
                resultado.pendencias()));

        return resultado;
    }

    /**
     * Fallback chamado em qualquer falha do happy path. Move proposta para {@link
     * StatusProposta#PENDENCIA} em transacao isolada — nao reusar EM tx do happy path porque ela
     * pode estar {@code rollback-only}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void moverParaPendencia(UUID propostaId) {
        PropostaCredito p = propostaRepository
                .findById(propostaId)
                .orElseThrow(() -> new PropostaNaoEncontradaException(propostaId));
        if (p.getStatus() == StatusProposta.PENDENCIA || p.getStatus().isFinal()) {
            return;
        }
        p.marcarPendencia();
        propostaRepository.save(p);
    }

    private void persistirTrilha(PropostaCredito proposta, ResultadoAvaliacaoCredito resultado) {
        scoreRepository.deleteByPropostaId(proposta.getId());
        regraRepository.deleteByPropostaId(proposta.getId());
        scoreRepository.save(ScoreInterno.calculado(
                proposta.getId(),
                resultado.score(),
                resultado.statusSugerido(),
                resultado.falhas(),
                resultado.pendencias()));
        for (RegraResultado r : resultado.regras()) {
            regraRepository.save(RegraCreditoAvaliada.registrar(
                    proposta.getId(), r.nome(), r.resultado(), r.motivo(), r.bloqueante()));
        }
    }
}
