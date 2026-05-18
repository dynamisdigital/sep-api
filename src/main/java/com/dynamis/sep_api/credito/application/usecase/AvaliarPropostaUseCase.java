package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.service.MotorRegrasCredito;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Avalia uma proposta criada executando o motor de regras (Sprint 8 Task 8.3). Disparado por
 * listener {@code AFTER_COMMIT} apos {@code PropostaCriadaEvent} pra desacoplar avaliacao da
 * criacao — falha do motor nao desfaz proposta persistida.
 *
 * <p>Persiste {@link ScoreInterno}, todas as {@link RegraCreditoAvaliada} avaliadas e aplica
 * sugestao no agregado. Se sugestao for {@link StatusProposta#REJEITADA}, grava {@link
 * DecisaoCredito} com origem {@link OrigemDecisao#MOTOR} e publica {@link PropostaRejeitadaEvent}.
 *
 * <p>Politica "sem auditoria, sem PRE_APROVADA": se persistencia da trilha (score/regras) falhar,
 * marca proposta como {@link StatusProposta#PENDENCIA} (Task 8.6 — auditoria reforcada).
 */
@Service
public class AvaliarPropostaUseCase {

    private static final Logger log = LoggerFactory.getLogger(AvaliarPropostaUseCase.class);

    private final PropostaCreditoRepository propostaRepository;
    private final ScoreInternoRepository scoreRepository;
    private final RegraCreditoAvaliadaRepository regraRepository;
    private final DecisaoCreditoRepository decisaoRepository;
    private final ConsultarOnboardingParaCreditoQuery onboardingQuery;
    private final MotorRegrasCredito motor;
    private final ApplicationEventPublisher eventPublisher;

    public AvaliarPropostaUseCase(
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoAvaliacaoCredito executar(UUID propostaId) {
        PropostaCredito proposta = propostaRepository
                .findById(propostaId)
                .orElseThrow(() -> new PropostaNaoEncontradaException(propostaId));

        OnboardingResumoCredito resumo = onboardingQuery
                .consultarPorId(proposta.getSolicitacaoOnboardingId())
                .orElseThrow(() -> new PropostaNaoEncontradaException(propostaId));

        ContextoAvaliacaoCredito contexto = new ContextoAvaliacaoCredito(
                proposta, resumo.tipoSolicitante(), resumo.status(), resumo.dataNascimento(), resumo.dataAbertura());

        ResultadoAvaliacaoCredito resultado = motor.avaliar(contexto);

        try {
            persistirTrilha(proposta, resultado);
        } catch (RuntimeException ex) {
            log.warn(
                    "Falha ao persistir trilha de credito para proposta {}; movendo para PENDENCIA.",
                    proposta.getId(),
                    ex);
            proposta.marcarPendencia();
            propostaRepository.save(proposta);
            return new ResultadoAvaliacaoCredito(
                    resultado.score(),
                    StatusProposta.PENDENCIA,
                    resultado.falhas(),
                    resultado.pendencias(),
                    resultado.regras());
        }

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
