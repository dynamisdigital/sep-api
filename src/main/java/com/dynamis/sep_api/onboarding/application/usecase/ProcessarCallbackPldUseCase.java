package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.domain.event.PldFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.PldHitDetectadoEvent;
import com.dynamis.sep_api.onboarding.domain.model.ConsultaPld;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.SeveridadePld;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaPldRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.RepresentanteLegalRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.onboarding.web.dto.CelcoinPldCallbackRequest;
import com.dynamis.sep_api.shared.application.usecase.RegistrarWebhookEventUseCase;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Processa callback consolidado do PLD Celcoin. Cada {@code AlvoResultado} no payload gera
 * registros {@link ConsultaPld} (1 por base, com hit ou limpo). Qualquer hit em qualquer alvo
 * move solicitacao para {@code REPROVADO_PLD}; todos limpos move para {@code APROVADO_FINAL}.
 *
 * <p>Idempotencia: outbox por {@code Idempotency-Key} + early-return se a solicitacao ja esta
 * em {@code APROVADO_FINAL}/{@code REPROVADO_PLD} (callback tardio).
 */
@Service
public class ProcessarCallbackPldUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarCallbackPldUseCase.class);
    private static final String PROVIDER = "celcoin-pld";
    private static final String EVENT = "callback";

    private final RegistrarWebhookEventUseCase registrarWebhookEventUseCase;
    private final WebhookEventLogRepository webhookEventLogRepository;
    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final KybEmpresaRepository kybRepository;
    private final RepresentanteLegalRepository representanteRepository;
    private final ConsultaPldRepository consultaPldRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProcessarCallbackPldUseCase(
            RegistrarWebhookEventUseCase registrarWebhookEventUseCase,
            WebhookEventLogRepository webhookEventLogRepository,
            SolicitacaoOnboardingRepository solicitacaoRepository,
            KybEmpresaRepository kybRepository,
            RepresentanteLegalRepository representanteRepository,
            ConsultaPldRepository consultaPldRepository,
            ApplicationEventPublisher eventPublisher) {
        this.registrarWebhookEventUseCase = registrarWebhookEventUseCase;
        this.webhookEventLogRepository = webhookEventLogRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.kybRepository = kybRepository;
        this.representanteRepository = representanteRepository;
        this.consultaPldRepository = consultaPldRepository;
        this.eventPublisher = eventPublisher;
    }

    public record Resultado(boolean aceito, boolean duplicado) {}

    @Transactional
    public Resultado executar(
            String idempotencyKey, String signature, String payloadCru, CelcoinPldCallbackRequest callback) {
        boolean gravado = registrarWebhookEventUseCase.executar(PROVIDER, EVENT, idempotencyKey, signature, payloadCru);
        if (!gravado) {
            log.info("Webhook PLD duplicado idempotencyKey={} — sem reprocessamento", idempotencyKey);
            return new Resultado(true, true);
        }

        WebhookEventLog evento = webhookEventLogRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "WebhookEventLog ausente apos registrar — inconsistencia transacional"));

        if (callback == null
                || callback.externalId() == null
                || callback.externalId().isBlank()) {
            evento.marcarFalhou("external_id ausente no payload");
            return new Resultado(true, false);
        }

        UUID solicitacaoId;
        try {
            solicitacaoId = UUID.fromString(callback.externalId());
        } catch (IllegalArgumentException ex) {
            evento.marcarFalhou("external_id nao e UUID");
            return new Resultado(true, false);
        }

        Optional<SolicitacaoOnboarding> solicitacaoOpt = solicitacaoRepository.findById(solicitacaoId);
        if (solicitacaoOpt.isEmpty()) {
            evento.marcarFalhou("solicitacao nao encontrada");
            return new Resultado(true, false);
        }
        SolicitacaoOnboarding solicitacao = solicitacaoOpt.get();

        if (solicitacao.getStatus() == StatusOnboarding.APROVADO_FINAL
                || solicitacao.getStatus() == StatusOnboarding.REPROVADO_PLD) {
            evento.marcarProcessado();
            log.info(
                    "Webhook PLD duplicado tardio idempotencyKey={} solicitacao={} status={}",
                    idempotencyKey,
                    solicitacaoId,
                    solicitacao.getStatus());
            return new Resultado(true, false);
        }
        if (solicitacao.getStatus() != StatusOnboarding.APROVADO) {
            evento.marcarFalhou("PLD callback sobre solicitacao nao APROVADA: " + solicitacao.getStatus());
            return new Resultado(true, false);
        }

        boolean houveHit = processarAlvos(solicitacaoId, solicitacao.getTipo(), callback, payloadCru);

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

        evento.marcarProcessado();
        log.info(
                "Webhook PLD processado idempotencyKey={} solicitacao={} status={}",
                idempotencyKey,
                solicitacaoId,
                statusFinal);
        return new Resultado(true, false);
    }

    private boolean processarAlvos(
            UUID solicitacaoId, TipoSolicitante tipo, CelcoinPldCallbackRequest callback, String payloadCru) {
        if (callback.alvos() == null || callback.alvos().isEmpty()) {
            return false;
        }
        boolean houveHit = false;
        List<RepresentanteLegal> representantes = kybRepository
                .findBySolicitacaoId(solicitacaoId)
                .map(k -> representanteRepository.findByKybEmpresaId(k.getId()))
                .orElse(List.of());

        for (CelcoinPldCallbackRequest.AlvoResultado alvo : callback.alvos()) {
            AlvoPld alvoTipo = mapearAlvo(alvo.alvoTipo(), tipo);
            String documento = alvo.documento();
            boolean alvoHit = false;
            for (CelcoinPldCallbackRequest.BaseResultado base : alvo.bases()) {
                BasePld basePld = mapearBase(base.base());
                if (basePld == null) continue;
                if (base.hit()) {
                    SeveridadePld severidade = mapearSeveridade(base.severidade());
                    consultaPldRepository.save(ConsultaPld.hit(
                            solicitacaoId,
                            alvoTipo,
                            documento,
                            basePld,
                            base.motivo(),
                            severidade,
                            base.dataInclusao(),
                            payloadCru));
                    eventPublisher.publishEvent(new PldHitDetectadoEvent(solicitacaoId, alvoTipo, basePld, severidade));
                    alvoHit = true;
                } else {
                    consultaPldRepository.save(
                            ConsultaPld.limpa(solicitacaoId, alvoTipo, documento, basePld, payloadCru));
                }
            }
            if (alvoTipo == AlvoPld.REPRESENTANTE) {
                for (RepresentanteLegal rep : representantes) {
                    if (rep.getCpf().equals(documento)) {
                        if (alvoHit) {
                            rep.marcarPldHit();
                        } else {
                            rep.marcarPldLimpo();
                        }
                        representanteRepository.save(rep);
                    }
                }
            }
            houveHit = houveHit || alvoHit;
        }
        return houveHit;
    }

    private AlvoPld mapearAlvo(String tipo, TipoSolicitante tipoSolicitante) {
        if (tipo == null) {
            return tipoSolicitante == TipoSolicitante.PESSOA ? AlvoPld.PESSOA : AlvoPld.EMPRESA;
        }
        return switch (tipo.toUpperCase()) {
            case "PESSOA", "PERSON" -> AlvoPld.PESSOA;
            case "EMPRESA", "COMPANY" -> AlvoPld.EMPRESA;
            case "REPRESENTANTE", "REPRESENTATIVE" -> AlvoPld.REPRESENTANTE;
            default -> AlvoPld.EMPRESA;
        };
    }

    private BasePld mapearBase(String base) {
        if (base == null) return null;
        return switch (base.toUpperCase()) {
            case "COAF" -> BasePld.COAF;
            case "OFAC" -> BasePld.OFAC;
            case "INTERPOL" -> BasePld.INTERPOL;
            case "MTE" -> BasePld.MTE;
            default -> null;
        };
    }

    private SeveridadePld mapearSeveridade(String severidade) {
        if (severidade == null) return null;
        return switch (severidade.toUpperCase()) {
            case "BAIXA", "LOW" -> SeveridadePld.BAIXA;
            case "MEDIA", "MEDIUM", "MODERATE" -> SeveridadePld.MEDIA;
            case "ALTA", "HIGH", "CRITICAL" -> SeveridadePld.ALTA;
            default -> null;
        };
    }
}
