package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.domain.event.PldFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.PldHitDetectadoEvent;
import com.dynamis.sep_api.onboarding.domain.event.PldLimpoEvent;
import com.dynamis.sep_api.onboarding.domain.model.ConsultaPld;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
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

        Optional<KybEmpresa> kybOpt = kybRepository.findBySolicitacaoId(solicitacaoId);
        List<RepresentanteLegal> representantes = kybOpt.map(k -> representanteRepository.findByKybEmpresaId(k.getId()))
                .orElse(List.of());
        String cnpjEsperado = kybOpt.map(KybEmpresa::getCnpj).orElse(null);

        // Valida completude + identidade do payload ANTES de qualquer transicao de status.
        String erroValidacao = validarPayload(
                callback, solicitacao.getTipo(), solicitacao.getDocumento(), cnpjEsperado, representantes);
        if (erroValidacao != null) {
            evento.marcarFalhou(erroValidacao);
            log.warn(
                    "Webhook PLD payload incompleto idempotencyKey={} solicitacao={} motivo={}",
                    idempotencyKey,
                    solicitacaoId,
                    erroValidacao);
            return new Resultado(true, false);
        }

        boolean houveHit;
        try {
            houveHit = processarAlvos(solicitacaoId, solicitacao.getTipo(), callback, payloadCru, representantes);
        } catch (RuntimeException ex) {
            evento.marcarFalhou("falha ao processar alvos: " + ex.getClass().getSimpleName());
            log.warn(
                    "Webhook PLD processamento falhou idempotencyKey={} solicitacao={}: {}",
                    idempotencyKey,
                    solicitacaoId,
                    ex.getMessage());
            return new Resultado(true, false);
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

        evento.marcarProcessado();
        log.info(
                "Webhook PLD processado idempotencyKey={} solicitacao={} status={}",
                idempotencyKey,
                solicitacaoId,
                statusFinal);
        return new Resultado(true, false);
    }

    private static final java.util.Set<BasePld> BASES_OBRIGATORIAS =
            java.util.Set.of(BasePld.COAF, BasePld.OFAC, BasePld.INTERPOL, BasePld.MTE);

    /**
     * Valida que o payload PLD cobre todos os alvos esperados com as 4 bases obrigatorias E que os
     * documentos do payload casam com a solicitacao em banco. Devolve {@code null} quando valido;
     * mensagem de erro sanitizada caso contrario.
     *
     * <p>PF: payload deve ter exatamente 1 alvo cujo {@code document_number} == {@code
     * solicitacao.documento}.
     *
     * <p>PJ: payload deve ter exatamente 1 alvo {@code EMPRESA} com {@code document_number} ==
     * {@code kyb.cnpj} + 1 alvo {@code REPRESENTANTE} por representante registrado (mesmo CPF). Alvos
     * com tipo desconhecido ou CPF/CNPJ alheios reprovam o callback.
     */
    private String validarPayload(
            CelcoinPldCallbackRequest callback,
            TipoSolicitante tipo,
            String documentoSolicitacao,
            String cnpjKyb,
            List<RepresentanteLegal> representantes) {
        if (callback.alvos() == null || callback.alvos().isEmpty()) {
            return "PLD payload vazio: results obrigatorio";
        }

        java.util.Map<String, AlvoNoCallback> alvosPorDocumento = new java.util.HashMap<>();
        for (CelcoinPldCallbackRequest.AlvoResultado alvo : callback.alvos()) {
            if (alvo == null) return "alvo nulo em results";
            if (alvo.documento() == null || alvo.documento().isBlank()) {
                return "alvo sem document_number";
            }
            if (alvo.bases() == null || alvo.bases().isEmpty()) {
                return "alvo " + mascararDocumento(alvo.documento()) + " sem databases";
            }
            AlvoPld tipoAlvo;
            try {
                tipoAlvo = mapearAlvo(alvo.alvoTipo(), tipo);
            } catch (IllegalArgumentException ex) {
                return "alvo " + mascararDocumento(alvo.documento()) + " com target_type desconhecido: "
                        + alvo.alvoTipo();
            }
            java.util.Set<BasePld> bases = new java.util.HashSet<>();
            for (CelcoinPldCallbackRequest.BaseResultado base : alvo.bases()) {
                if (base == null) return "base nula em databases";
                BasePld basePld = mapearBase(base.base());
                if (basePld != null) bases.add(basePld);
            }
            if (!bases.containsAll(BASES_OBRIGATORIAS)) {
                java.util.Set<BasePld> faltando = new java.util.HashSet<>(BASES_OBRIGATORIAS);
                faltando.removeAll(bases);
                return "alvo " + mascararDocumento(alvo.documento()) + " sem bases obrigatorias: " + faltando;
            }
            if (alvosPorDocumento.put(alvo.documento(), new AlvoNoCallback(tipoAlvo, bases)) != null) {
                return "documento " + mascararDocumento(alvo.documento()) + " repetido em results";
            }
        }

        if (tipo == TipoSolicitante.PESSOA) {
            if (alvosPorDocumento.size() != 1) {
                return "PF deve ter exatamente 1 alvo; recebido=" + alvosPorDocumento.size();
            }
            AlvoNoCallback unico = alvosPorDocumento.get(documentoSolicitacao);
            if (unico == null) {
                return "alvo PF nao corresponde ao documento da solicitacao";
            }
            if (unico.tipo() != AlvoPld.PESSOA) {
                return "alvo PF com tipo incorreto: " + unico.tipo();
            }
            return null;
        }

        // PJ
        if (cnpjKyb == null) {
            return "PJ sem KybEmpresa registrada — callback nao deveria chegar antes do KYB";
        }
        AlvoNoCallback alvoEmpresa = alvosPorDocumento.get(cnpjKyb);
        if (alvoEmpresa == null) {
            return "callback PJ nao traz alvo EMPRESA com CNPJ da solicitacao";
        }
        if (alvoEmpresa.tipo() != AlvoPld.EMPRESA) {
            return "alvo do CNPJ " + mascararDocumento(cnpjKyb) + " com tipo incorreto: " + alvoEmpresa.tipo();
        }
        java.util.Set<String> cpfsEsperados = new java.util.HashSet<>();
        for (RepresentanteLegal rep : representantes) {
            cpfsEsperados.add(rep.getCpf());
            AlvoNoCallback alvoRep = alvosPorDocumento.get(rep.getCpf());
            if (alvoRep == null) {
                return "representante " + mascararDocumento(rep.getCpf()) + " ausente no callback";
            }
            if (alvoRep.tipo() != AlvoPld.REPRESENTANTE) {
                return "representante " + mascararDocumento(rep.getCpf()) + " com tipo incorreto: " + alvoRep.tipo();
            }
        }
        // Qualquer outro documento alem do CNPJ + CPFs esperados e rejeitado.
        for (String doc : alvosPorDocumento.keySet()) {
            if (!doc.equals(cnpjKyb) && !cpfsEsperados.contains(doc)) {
                return "callback PJ contem documento nao esperado: " + mascararDocumento(doc);
            }
        }
        return null;
    }

    private record AlvoNoCallback(AlvoPld tipo, java.util.Set<BasePld> bases) {}

    private static String mascararDocumento(String documento) {
        if (documento == null || documento.length() < 6) return "***";
        int len = documento.length();
        return documento.substring(0, 3) + "*".repeat(len - 5) + documento.substring(len - 2);
    }

    private boolean processarAlvos(
            UUID solicitacaoId,
            TipoSolicitante tipo,
            CelcoinPldCallbackRequest callback,
            String payloadCru,
            List<RepresentanteLegal> representantes) {
        boolean houveHit = false;

        // Sprint 15 Task 15.4 (15F-025): index representantes por CPF pra lookup O(1) em vez de
        // varrer a lista por alvo REPRESENTANTE. Mantem mesma semantica (multiplos representantes
        // com mesmo CPF — improvavel mas possivel — sao todos atualizados).
        java.util.Map<String, java.util.List<RepresentanteLegal>> representantesPorCpf = new java.util.HashMap<>();
        for (RepresentanteLegal rep : representantes) {
            representantesPorCpf.computeIfAbsent(rep.getCpf(), k -> new java.util.ArrayList<>()).add(rep);
        }

        for (CelcoinPldCallbackRequest.AlvoResultado alvo : callback.alvos()) {
            AlvoPld alvoTipo = mapearAlvo(alvo.alvoTipo(), tipo);
            String documento = alvo.documento();
            boolean alvoHit = false;
            for (CelcoinPldCallbackRequest.BaseResultado base : alvo.bases()) {
                if (base == null) continue;
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
                    eventPublisher.publishEvent(
                            new PldHitDetectadoEvent(solicitacaoId, alvoTipo, basePld, severidade, base.motivo()));
                    alvoHit = true;
                } else {
                    consultaPldRepository.save(
                            ConsultaPld.limpa(solicitacaoId, alvoTipo, documento, basePld, payloadCru));
                }
            }
            if (alvoTipo == AlvoPld.REPRESENTANTE) {
                List<RepresentanteLegal> matches = representantesPorCpf.getOrDefault(documento, List.of());
                for (RepresentanteLegal rep : matches) {
                    if (alvoHit) {
                        rep.marcarPldHit();
                    } else {
                        rep.marcarPldLimpo();
                    }
                }
                if (!matches.isEmpty()) {
                    representanteRepository.saveAll(matches);
                }
            }
            if (!alvoHit) {
                eventPublisher.publishEvent(new PldLimpoEvent(solicitacaoId, alvoTipo, mascararDocumento(documento)));
            }
            houveHit = houveHit || alvoHit;
        }
        return houveHit;
    }

    private AlvoPld mapearAlvo(String tipo, TipoSolicitante tipoSolicitante) {
        if (tipo == null) {
            // Fallback explicito ao tipo da solicitacao — provider pode nao expor target_type.
            return tipoSolicitante == TipoSolicitante.PESSOA ? AlvoPld.PESSOA : AlvoPld.EMPRESA;
        }
        return switch (tipo.toUpperCase()) {
            case "PESSOA", "PERSON" -> AlvoPld.PESSOA;
            case "EMPRESA", "COMPANY" -> AlvoPld.EMPRESA;
            case "REPRESENTANTE", "REPRESENTATIVE" -> AlvoPld.REPRESENTANTE;
            default -> throw new IllegalArgumentException("target_type desconhecido: " + tipo);
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
