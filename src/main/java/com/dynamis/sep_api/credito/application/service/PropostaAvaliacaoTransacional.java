package com.dynamis.sep_api.credito.application.service;

import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import com.dynamis.sep_api.credito.application.service.dto.ResultadoAvaliacaoCredito;
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
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Operacoes transacionais isoladas do {@code AvaliarPropostaUseCase} (Sprint 8 Task 8.3).
 *
 * <p>Cada metodo abre uma transacao propria com {@code REQUIRES_NEW} pra que o {@code
 * AvaliarPropostaUseCase} (sem {@code @Transactional} na entry) possa orquestrar happy path e
 * fallback de {@link StatusProposta#PENDENCIA} em transacoes distintas — caso a transacao do
 * happy path entre em estado {@code rollback-only}, o fallback ainda consegue persistir o status.
 *
 * <p>Sprint 8 fix code review Task 8.6: a gravacao de {@link TipoEventoSeguranca#PROPOSTA_AVALIADA_MOTOR}
 * acontece SINCRONA dentro desta transacao (antes de retornar) — Step 008.6.3 do spec exige que
 * "motor nao pode promover status sem auditoria". Se a gravacao do audit log falhar, a transacao
 * sofre rollback e o {@code AvaliarPropostaUseCase} captura via catch global, chamando o
 * fallback {@code moverParaPendencia} em transacao isolada. Os demais eventos
 * ({@code PROPOSTA_CRIADA}, {@code PARECER_REGISTRADO}, {@code PROPOSTA_APROVADA},
 * {@code PROPOSTA_REJEITADA}) continuam via listener AFTER_COMMIT — esses sao registrados apos
 * o estado ja persistido e nao tem o requisito regulatorio de "garantia antes de promocao".
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
    private final AuditLogSegurancaService auditLogService;
    private final ObjectMapper objectMapper;

    public PropostaAvaliacaoTransacional(
            PropostaCreditoRepository propostaRepository,
            ScoreInternoRepository scoreRepository,
            RegraCreditoAvaliadaRepository regraRepository,
            DecisaoCreditoRepository decisaoRepository,
            ConsultarOnboardingParaCreditoQuery onboardingQuery,
            MotorRegrasCredito motor,
            ApplicationEventPublisher eventPublisher,
            AuditLogSegurancaService auditLogService,
            ObjectMapper objectMapper) {
        this.propostaRepository = propostaRepository;
        this.scoreRepository = scoreRepository;
        this.regraRepository = regraRepository;
        this.decisaoRepository = decisaoRepository;
        this.onboardingQuery = onboardingQuery;
        this.motor = motor;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * Happy path: carrega proposta + onboarding, executa motor, persiste trilha (score + regras),
     * GRAVA audit log {@code PROPOSTA_AVALIADA_MOTOR} sincrono, aplica sugestao no agregado. Em
     * rejeicao automatica, grava {@link DecisaoCredito} com origem {@link OrigemDecisao#MOTOR} e
     * publica {@link PropostaRejeitadaEvent}.
     *
     * <p>Auditoria sincrona = Step 008.6.3: falha no audit -> exception propaga -> tx rollback
     * -> orquestrador marca PENDENCIA.
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
        gravarAuditMotorSincrono(proposta, resultado);
        proposta.aplicarSugestaoMotor(resultado.statusSugerido());
        propostaRepository.save(proposta);

        if (resultado.statusSugerido() == StatusProposta.REJEITADA) {
            decisaoRepository.save(
                    DecisaoCredito.porMotor(proposta.getId(), StatusProposta.REJEITADA, resultado.score()));
            eventPublisher.publishEvent(
                    new PropostaRejeitadaEvent(proposta.getId(), proposta.getTomadorId(), OrigemDecisao.MOTOR, null));
        }

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

    private void gravarAuditMotorSincrono(PropostaCredito proposta, ResultadoAvaliacaoCredito resultado) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propostaId", proposta.getId().toString());
        payload.put("score", resultado.score());
        payload.put("statusSugerido", resultado.statusSugerido().name());
        payload.put("falhas", resultado.falhas());
        payload.put("pendencias", resultado.pendencias());
        String detalhes;
        try {
            detalhes = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            // Falha de serializacao = falha de auditoria = rollback (Step 008.6.3).
            throw new IllegalStateException("Falha ao serializar audit log de PROPOSTA_AVALIADA_MOTOR", ex);
        }
        auditLogService.gravar(TipoEventoSeguranca.PROPOSTA_AVALIADA_MOTOR, proposta.getTomadorId(), detalhes);
    }
}
