package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.dto.CriarPropostaCreditoCommand;
import com.dynamis.sep_api.credito.domain.event.PropostaCriadaEvent;
import com.dynamis.sep_api.credito.domain.exception.OnboardingNaoAprovadoException;
import com.dynamis.sep_api.credito.domain.exception.OwnershipPropostaException;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.onboarding.application.query.ConsultarOnboardingParaCreditoQuery;
import com.dynamis.sep_api.onboarding.application.query.OnboardingResumoCredito;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria uma {@link PropostaCredito} para tomador autenticado. Pre-condicoes regulatorias (Sprint 8
 * Task 8.3):
 *
 * <ul>
 *   <li>Solicitacao de onboarding referenciada deve existir (404);
 *   <li>Solicitacao deve pertencer ao mesmo tomador — ownership (403);
 *   <li>Status do onboarding deve ser {@link StatusOnboarding#APROVADO_FINAL} (422);
 *   <li>Demais validacoes de valor/prazo via factory {@code PropostaCredito.criar}.
 * </ul>
 *
 * <p>Apos persistir, publica {@link PropostaCriadaEvent} pro listener disparar avaliacao do motor
 * em transacao separada ({@code AFTER_COMMIT}), pra que falha no motor nao desfaca a criacao.
 */
@Service
public class CriarPropostaCreditoUseCase {

    private final PropostaCreditoRepository propostaRepository;
    private final ConsultarOnboardingParaCreditoQuery onboardingQuery;
    private final ApplicationEventPublisher eventPublisher;

    public CriarPropostaCreditoUseCase(
            PropostaCreditoRepository propostaRepository,
            ConsultarOnboardingParaCreditoQuery onboardingQuery,
            ApplicationEventPublisher eventPublisher) {
        this.propostaRepository = propostaRepository;
        this.onboardingQuery = onboardingQuery;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PropostaCredito executar(CriarPropostaCreditoCommand cmd) {
        OnboardingResumoCredito resumo = onboardingQuery
                .consultarPorId(cmd.solicitacaoOnboardingId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "ONB-404-001",
                        "Solicitacao de onboarding " + cmd.solicitacaoOnboardingId() + " nao encontrada"));

        if (!resumo.usuarioId().equals(cmd.tomadorId())) {
            throw new OwnershipPropostaException("Solicitacao de onboarding pertence a outro tomador");
        }

        if (resumo.status() != StatusOnboarding.APROVADO_FINAL) {
            throw new OnboardingNaoAprovadoException(resumo.status());
        }

        PropostaCredito proposta = PropostaCredito.criar(
                cmd.tomadorId(),
                cmd.solicitacaoOnboardingId(),
                cmd.tipoOperacao(),
                new Money(cmd.valorSolicitado(), "BRL"),
                cmd.prazoMeses() == null ? 0 : cmd.prazoMeses());

        PropostaCredito salva = propostaRepository.save(proposta);
        eventPublisher.publishEvent(
                new PropostaCriadaEvent(salva.getId(), salva.getTomadorId(), salva.getSolicitacaoOnboardingId()));
        return salva;
    }
}
