package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.port.out.BackgroundCheckProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.HitPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaPld;
import com.dynamis.sep_api.onboarding.domain.event.PldFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.PldHitDetectadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.PldIniciadoEvent;
import com.dynamis.sep_api.onboarding.domain.exception.KybNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.ConsultaPld;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaPldRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.RepresentanteLegalRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.dynamis.sep_api.shared.integration.IdempotencyKeyInterceptor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Executa consulta PLD para uma solicitacao PJ apos KYB {@code APROVADO}. Consulta a empresa +
 * cada representante legal nas 4 bases obrigatorias. Qualquer hit em empresa OU em qualquer
 * representante bloqueia onboarding ({@code REPROVADO_PLD}); todos limpos move pra
 * {@code APROVADO_FINAL}.
 *
 * <p>Idempotency-Key por alvo: {@code solicitacaoId + ":pld:" + documentoAlvo + ":r" + revisao}.
 */
@Service
public class IniciarPldEmpresaUseCase {

    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final KybEmpresaRepository kybRepository;
    private final RepresentanteLegalRepository representanteRepository;
    private final ConsultaPldRepository consultaPldRepository;
    private final BackgroundCheckProvider provider;
    private final ApplicationEventPublisher eventPublisher;

    public IniciarPldEmpresaUseCase(
            SolicitacaoOnboardingRepository solicitacaoRepository,
            KybEmpresaRepository kybRepository,
            RepresentanteLegalRepository representanteRepository,
            ConsultaPldRepository consultaPldRepository,
            BackgroundCheckProvider provider,
            ApplicationEventPublisher eventPublisher) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.kybRepository = kybRepository;
        this.representanteRepository = representanteRepository;
        this.consultaPldRepository = consultaPldRepository;
        this.provider = provider;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public StatusOnboarding executar(UUID solicitacaoId, String correlationId) {
        SolicitacaoOnboarding solicitacao = solicitacaoRepository
                .findById(solicitacaoId)
                .orElseThrow(() -> new OnboardingNaoEncontradoException(solicitacaoId));
        if (solicitacao.getTipo() != TipoSolicitante.EMPRESA) {
            throw new ValidacaoException("ONB-400-011", "PLD empresa exige solicitacao EMPRESA");
        }
        if (solicitacao.getStatus() != StatusOnboarding.APROVADO) {
            throw new ValidacaoException(
                    "ONB-400-012", "PLD so dispara a partir de APROVADO; atual=" + solicitacao.getStatus());
        }

        KybEmpresa kyb = kybRepository
                .findBySolicitacaoId(solicitacaoId)
                .orElseThrow(() -> new KybNaoEncontradoException(solicitacaoId));
        List<RepresentanteLegal> representantes = representanteRepository.findByKybEmpresaId(kyb.getId());

        boolean houveHit = false;

        // Empresa
        boolean hitEmpresa = consultarAlvo(
                solicitacaoId,
                AlvoPld.EMPRESA,
                kyb.getCnpj(),
                kyb.getRazaoSocial(),
                solicitacao.getRevisaoDocumentos(),
                correlationId,
                true);
        houveHit = houveHit || hitEmpresa;

        // Representantes
        for (RepresentanteLegal rep : representantes) {
            boolean hitRep = consultarAlvo(
                    solicitacaoId,
                    AlvoPld.REPRESENTANTE,
                    rep.getCpf(),
                    rep.getNome(),
                    solicitacao.getRevisaoDocumentos(),
                    correlationId,
                    false);
            if (hitRep) {
                rep.marcarPldHit();
                houveHit = true;
            } else {
                rep.marcarPldLimpo();
            }
            representanteRepository.save(rep);
        }

        StatusOnboarding statusFinal;
        if (houveHit) {
            solicitacao.reprovarPorPld();
            statusFinal = StatusOnboarding.REPROVADO_PLD;
        } else {
            solicitacao.marcarAprovadoFinal();
            statusFinal = StatusOnboarding.APROVADO_FINAL;
        }
        solicitacaoRepository.save(solicitacao);
        eventPublisher.publishEvent(new PldFinalizadoEvent(solicitacaoId, solicitacao.getUsuarioId(), statusFinal));
        return statusFinal;
    }

    /** Devolve {@code true} se o alvo teve ao menos um hit em alguma base. */
    private boolean consultarAlvo(
            UUID solicitacaoId,
            AlvoPld alvoTipo,
            String documento,
            String nome,
            int revisao,
            String correlationId,
            boolean isEmpresa) {
        eventPublisher.publishEvent(
                new PldIniciadoEvent(solicitacaoId, alvoTipo, IniciarPldPessoaUseCase.mascararDocumento(documento)));

        String idempotencyKey = solicitacaoId + ":pld:" + documento + ":r" + revisao;
        String mdcPrevio = MDC.get(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY);
        MDC.put(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY, idempotencyKey);
        try {
            RequisicaoPld requisicao = RequisicaoPld.comBasesObrigatorias(solicitacaoId, alvoTipo, nome, documento);
            RespostaPld resposta = isEmpresa
                    ? provider.consultarEmpresa(requisicao, correlationId)
                    : provider.consultarPessoa(requisicao, correlationId);

            persistirResultados(solicitacaoId, alvoTipo, documento, resposta);

            if (!resposta.limpo()) {
                for (HitPld hit : resposta.hits()) {
                    eventPublisher.publishEvent(
                            new PldHitDetectadoEvent(solicitacaoId, alvoTipo, hit.base(), hit.severidade()));
                }
                return true;
            }
            return false;
        } finally {
            if (mdcPrevio == null) {
                MDC.remove(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY);
            } else {
                MDC.put(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY, mdcPrevio);
            }
        }
    }

    private void persistirResultados(UUID solicitacaoId, AlvoPld alvoTipo, String documento, RespostaPld resposta) {
        Set<BasePld> obrigatorias = Set.of(BasePld.COAF, BasePld.OFAC, BasePld.INTERPOL, BasePld.MTE);
        if (resposta.limpo()) {
            for (BasePld base : obrigatorias) {
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
        for (BasePld base : obrigatorias) {
            if (!basesComHit.contains(base)) {
                consultaPldRepository.save(
                        ConsultaPld.limpa(solicitacaoId, alvoTipo, documento, base, resposta.payloadProvider()));
            }
        }
    }
}
