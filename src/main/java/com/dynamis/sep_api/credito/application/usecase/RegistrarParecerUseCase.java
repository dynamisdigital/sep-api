package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.dto.RegistrarParecerCommand;
import com.dynamis.sep_api.credito.domain.event.ParecerRegistradoEvent;
import com.dynamis.sep_api.credito.domain.event.PropostaAprovadaEvent;
import com.dynamis.sep_api.credito.domain.event.PropostaRejeitadaEvent;
import com.dynamis.sep_api.credito.domain.exception.PropostaInvalidaException;
import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.DecisaoCredito;
import com.dynamis.sep_api.credito.domain.model.ParecerCredito;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.model.ScoreInterno;
import com.dynamis.sep_api.credito.domain.vo.OrigemDecisao;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.infrastructure.persistence.DecisaoCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ParecerCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ScoreInternoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra um parecer manual de operador financeiro sobre uma proposta (Sprint 8 Task 8.4).
 * Autorizacao ({@code ROLE_FINANCEIRO} + step-up token) fica no endpoint via Spring Security +
 * {@code @RequireStepUp}.
 *
 * <p>Comportamento:
 *
 * <ul>
 *   <li>Proposta deve existir (404 caso contrario);
 *   <li>Proposta nao pode estar em estado final (400);
 *   <li>Justificativa obrigatoria com 10-1000 chars (validado em {@code ParecerCredito.registrar});
 *   <li>Versao calculada como max(versao_anterior)+1 — persistencia rejeita duplicata pelo unique
 *       {@code (proposta_id, versao)};
 *   <li>Score do motor atual e snapshot pra trilha auditavel comparar sugestao vs decisao manual;
 *   <li>Decisao manual SOBREPOE sugestao do motor (CMN 4.656/2018 Art. 9);
 *   <li>{@code APROVAR}/{@code REJEITAR} -> grava {@link DecisaoCredito#porParecer} + publica
 *       evento de dominio correspondente;
 *   <li>{@code PENDENCIA} -> apenas transiciona proposta, sem decisao final.
 * </ul>
 */
@Service
public class RegistrarParecerUseCase {

    private final PropostaCreditoRepository propostaRepository;
    private final ParecerCreditoRepository parecerRepository;
    private final ScoreInternoRepository scoreRepository;
    private final DecisaoCreditoRepository decisaoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RegistrarParecerUseCase(
            PropostaCreditoRepository propostaRepository,
            ParecerCreditoRepository parecerRepository,
            ScoreInternoRepository scoreRepository,
            DecisaoCreditoRepository decisaoRepository,
            ApplicationEventPublisher eventPublisher) {
        this.propostaRepository = propostaRepository;
        this.parecerRepository = parecerRepository;
        this.scoreRepository = scoreRepository;
        this.decisaoRepository = decisaoRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ParecerCredito executar(RegistrarParecerCommand cmd) {
        // Lock pessimista pra serializar pareceres concorrentes na mesma proposta: evita 2
        // requests calcularem a mesma versao e baterem na unique uq_parecer_credito_versao.
        PropostaCredito proposta = propostaRepository
                .findByIdForUpdate(cmd.propostaId())
                .orElseThrow(() -> new PropostaNaoEncontradaException(cmd.propostaId()));

        if (proposta.getStatus().isFinal()) {
            throw new PropostaInvalidaException(
                    "Proposta ja esta em estado final " + proposta.getStatus() + "; novo parecer nao permitido");
        }

        Integer scoreMotor = scoreRepository
                .findByPropostaId(proposta.getId())
                .map(ScoreInterno::getValor)
                .orElse(null);

        int proximaVersao = (int) (parecerRepository.countByPropostaId(proposta.getId()) + 1);

        ParecerCredito parecer = ParecerCredito.registrar(
                proposta.getId(), cmd.pareceristaId(), cmd.decisao(), cmd.justificativa(), scoreMotor, proximaVersao);
        ParecerCredito salvo = parecerRepository.save(parecer);

        proposta.registrarDecisaoManual(cmd.decisao());
        propostaRepository.save(proposta);

        StatusProposta novoStatus = proposta.getStatus();
        if (novoStatus == StatusProposta.APROVADA || novoStatus == StatusProposta.REJEITADA) {
            decisaoRepository.save(DecisaoCredito.porParecer(proposta.getId(), novoStatus, scoreMotor, salvo.getId()));
            if (novoStatus == StatusProposta.APROVADA) {
                eventPublisher.publishEvent(
                        new PropostaAprovadaEvent(proposta.getId(), proposta.getTomadorId(), salvo.getId()));
            } else {
                eventPublisher.publishEvent(new PropostaRejeitadaEvent(
                        proposta.getId(), proposta.getTomadorId(), OrigemDecisao.MANUAL, salvo.getId()));
            }
        }

        eventPublisher.publishEvent(new ParecerRegistradoEvent(
                proposta.getId(),
                salvo.getId(),
                salvo.getPareceristaId(),
                salvo.getDecisao(),
                salvo.getJustificativa(),
                scoreMotor));

        return salvo;
    }
}
