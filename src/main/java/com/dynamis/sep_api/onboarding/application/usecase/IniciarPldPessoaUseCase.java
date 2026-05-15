package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.port.out.BackgroundCheckProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.HitPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaPld;
import com.dynamis.sep_api.onboarding.domain.event.PldFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.PldHitDetectadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.PldIniciadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.PldLimpoEvent;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.ConsultaPld;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaPldRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.dynamis.sep_api.shared.integration.IdempotencyKeyInterceptor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Executa consulta PLD para uma solicitacao PF apos KYC {@code APROVADO}. Persiste {@link ConsultaPld}
 * por base e move a {@code SolicitacaoOnboarding} para {@code APROVADO_FINAL} (limpo) ou
 * {@code REPROVADO_PLD} (qualquer hit em qualquer base).
 *
 * <p>Idempotency-Key deterministica: {@code solicitacaoId + ":pld:" + documento + ":r" + revisao}.
 */
@Service
public class IniciarPldPessoaUseCase {

    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final ConsultaPldRepository consultaPldRepository;
    private final BackgroundCheckProvider provider;
    private final ApplicationEventPublisher eventPublisher;

    public IniciarPldPessoaUseCase(
            SolicitacaoOnboardingRepository solicitacaoRepository,
            ConsultaPldRepository consultaPldRepository,
            BackgroundCheckProvider provider,
            ApplicationEventPublisher eventPublisher) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.consultaPldRepository = consultaPldRepository;
        this.provider = provider;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public StatusOnboarding executar(UUID solicitacaoId, String correlationId) {
        SolicitacaoOnboarding solicitacao = solicitacaoRepository
                .findById(solicitacaoId)
                .orElseThrow(() -> new OnboardingNaoEncontradoException(solicitacaoId));
        if (solicitacao.getTipo() != TipoSolicitante.PESSOA) {
            throw new ValidacaoException("ONB-400-009", "PLD pessoa fisica exige solicitacao PESSOA");
        }
        // Idempotente: re-execucao apos consolidacao retorna status ja persistido sem novo side
        // effect (provider, persistencia, eventos).
        if (solicitacao.getStatus() == StatusOnboarding.APROVADO_FINAL
                || solicitacao.getStatus() == StatusOnboarding.REPROVADO_PLD) {
            return solicitacao.getStatus();
        }
        if (solicitacao.getStatus() != StatusOnboarding.APROVADO) {
            throw new ValidacaoException(
                    "ONB-400-010", "PLD so dispara a partir de APROVADO; atual=" + solicitacao.getStatus());
        }

        eventPublisher.publishEvent(
                new PldIniciadoEvent(solicitacaoId, AlvoPld.PESSOA, mascararDocumento(solicitacao.getDocumento())));

        String idempotencyKey =
                solicitacaoId + ":pld:" + solicitacao.getDocumento() + ":r" + solicitacao.getRevisaoDocumentos();
        String mdcPrevio = MDC.get(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY);
        MDC.put(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY, idempotencyKey);
        try {
            RespostaPld resposta = provider.consultarPessoa(
                    RequisicaoPld.comBasesObrigatorias(
                            solicitacaoId, AlvoPld.PESSOA, solicitacao.getNomeCompleto(), solicitacao.getDocumento()),
                    correlationId);

            persistirResultados(solicitacaoId, AlvoPld.PESSOA, solicitacao.getDocumento(), resposta);

            StatusOnboarding statusFinal;
            if (resposta.limpo()) {
                solicitacao.marcarAprovadoFinal();
                statusFinal = StatusOnboarding.APROVADO_FINAL;
                eventPublisher.publishEvent(new PldLimpoEvent(
                        solicitacaoId, AlvoPld.PESSOA, mascararDocumento(solicitacao.getDocumento())));
            } else {
                solicitacao.reprovarPorPld();
                statusFinal = StatusOnboarding.REPROVADO_PLD;
                publicarHits(solicitacaoId, AlvoPld.PESSOA, resposta);
            }
            solicitacaoRepository.save(solicitacao);
            eventPublisher.publishEvent(new PldFinalizadoEvent(solicitacaoId, solicitacao.getUsuarioId(), statusFinal));
            return statusFinal;
        } finally {
            if (mdcPrevio == null) {
                MDC.remove(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY);
            } else {
                MDC.put(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY, mdcPrevio);
            }
        }
    }

    private void persistirResultados(UUID solicitacaoId, AlvoPld alvoTipo, String documento, RespostaPld resposta) {
        if (resposta.limpo()) {
            // Persiste 1 registro limpo consolidado por base obrigatoria.
            for (BasePld base : Set.of(BasePld.COAF, BasePld.OFAC, BasePld.INTERPOL, BasePld.MTE)) {
                consultaPldRepository.save(
                        ConsultaPld.limpa(solicitacaoId, alvoTipo, documento, base, resposta.payloadProvider()));
            }
            return;
        }
        Set<BasePld> basesComHit = new HashSet<>();
        for (HitPld hit : resposta.hits()) {
            consultaPldRepository.save(ConsultaPld.hit(
                    solicitacaoId,
                    alvoTipo,
                    documento,
                    hit.base(),
                    hit.motivo(),
                    hit.severidade(),
                    hit.dataInclusao(),
                    hit.payloadProvider()));
            basesComHit.add(hit.base());
        }
        // Bases que nao tiveram hit ainda assim sao registradas como consulta limpa, pra trilha completa.
        for (BasePld base : Set.of(BasePld.COAF, BasePld.OFAC, BasePld.INTERPOL, BasePld.MTE)) {
            if (!basesComHit.contains(base)) {
                consultaPldRepository.save(
                        ConsultaPld.limpa(solicitacaoId, alvoTipo, documento, base, resposta.payloadProvider()));
            }
        }
    }

    private void publicarHits(UUID solicitacaoId, AlvoPld alvoTipo, RespostaPld resposta) {
        for (HitPld hit : resposta.hits()) {
            eventPublisher.publishEvent(
                    new PldHitDetectadoEvent(solicitacaoId, alvoTipo, hit.base(), hit.severidade(), hit.motivo()));
        }
    }

    /** Mascara documento mantendo apenas primeiros 3 + ultimos 2 digitos. */
    static String mascararDocumento(String documento) {
        if (documento == null || documento.length() < 6) return "***";
        int len = documento.length();
        return documento.substring(0, 3) + "*".repeat(len - 5) + documento.substring(len - 2);
    }
}
