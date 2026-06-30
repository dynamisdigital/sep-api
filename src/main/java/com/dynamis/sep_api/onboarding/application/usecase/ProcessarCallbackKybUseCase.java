package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.domain.event.KybFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.exception.KybNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.ConsultaCNPJ;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.SituacaoCadastral;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaCNPJRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.RepresentanteLegalRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.onboarding.web.dto.CelcoinKybCallbackRequest;
import com.dynamis.sep_api.shared.application.usecase.RegistrarWebhookEventUseCase;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Processa callback assincrono do KYB Celcoin. Reusa o outbox da Sprint 4 e segue o mesmo padrao
 * de {@code ProcessarCallbackKycUseCase}.
 *
 * <p>Fluxo:
 *
 * <ol>
 *   <li>Grava o evento no outbox com {@code idempotencyKey}; duplicado encerra com 202.
 *   <li>Resolve a solicitacao a partir do {@code external_id} (= {@code solicitacaoId}).
 *   <li>Se a solicitacao ja esta em status final (KYB ja processado sincronamente na Task 7.5),
 *       trata como duplicado tardio: marca evento PROCESSADO sem reescrever resultado.
 *   <li>Caso contrario, persiste {@link ConsultaCNPJ}, finaliza solicitacao (situacao
 *       {@code ATIVA} -> {@code APROVADO}; demais -> {@code REPROVADO}) e publica
 *       {@link KybFinalizadoEvent}. {@code APROVADO} dispara PLD via PldOrchestrationListener.
 * </ol>
 */
@Service
public class ProcessarCallbackKybUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarCallbackKybUseCase.class);
    private static final String PROVIDER = "celcoin-kyb";
    private static final String EVENT = "callback";

    private final RegistrarWebhookEventUseCase registrarWebhookEventUseCase;
    private final WebhookEventLogRepository webhookEventLogRepository;
    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final KybEmpresaRepository kybRepository;
    private final ConsultaCNPJRepository consultaCnpjRepository;
    private final RepresentanteLegalRepository representanteRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProcessarCallbackKybUseCase(
            RegistrarWebhookEventUseCase registrarWebhookEventUseCase,
            WebhookEventLogRepository webhookEventLogRepository,
            SolicitacaoOnboardingRepository solicitacaoRepository,
            KybEmpresaRepository kybRepository,
            ConsultaCNPJRepository consultaCnpjRepository,
            RepresentanteLegalRepository representanteRepository,
            ApplicationEventPublisher eventPublisher) {
        this.registrarWebhookEventUseCase = registrarWebhookEventUseCase;
        this.webhookEventLogRepository = webhookEventLogRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.kybRepository = kybRepository;
        this.consultaCnpjRepository = consultaCnpjRepository;
        this.representanteRepository = representanteRepository;
        this.eventPublisher = eventPublisher;
    }

    public record Resultado(boolean aceito, boolean duplicado) {}

    @Transactional
    public Resultado executar(
            String idempotencyKey, String signature, String payloadCru, CelcoinKybCallbackRequest callback) {
        boolean gravado = registrarWebhookEventUseCase.executar(PROVIDER, EVENT, idempotencyKey, signature, payloadCru);
        if (!gravado) {
            log.info("Webhook KYB duplicado — sem reprocessamento");
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
            log.warn("Webhook KYB sem external_id");
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
            log.warn("Webhook KYB sem solicitacao correspondente");
            return new Resultado(true, false);
        }
        SolicitacaoOnboarding solicitacao = solicitacaoOpt.get();

        if (solicitacao.getStatus().isFinal()) {
            evento.marcarProcessado();
            log.info(
                    "Webhook KYB duplicado tardio solicitacao={} status={}",
                    solicitacao.getId(),
                    solicitacao.getStatus());
            return new Resultado(true, false);
        }

        KybEmpresa kyb;
        try {
            kyb = kybRepository
                    .findBySolicitacaoId(solicitacaoId)
                    .orElseThrow(() -> new KybNaoEncontradoException(solicitacaoId));
        } catch (KybNaoEncontradoException ex) {
            evento.marcarFalhou("KybEmpresa ausente para solicitacao");
            return new Resultado(true, false);
        }

        SituacaoCadastral situacao = mapearSituacao(callback.situacao());
        persistirConsultaCnpj(kyb.getId(), situacao, callback, payloadCru);

        // Se a solicitacao esta em EM_VERIFICACAO, finaliza. Caso contrario (ja em DOCUMENTOS_RECEBIDOS
        // sem ter passado por marcarEmVerificacao via sync), dispara verificacao primeiro.
        if (solicitacao.getStatus() != StatusOnboarding.EM_VERIFICACAO) {
            solicitacao.registrarDocumentoEnviado(); // garante INICIADO -> DOCUMENTOS_RECEBIDOS se aplicavel
            try {
                solicitacao.marcarEmVerificacao("kyb-async-" + solicitacaoId);
            } catch (RuntimeException trans) {
                evento.marcarFalhou("transicao invalida: " + solicitacao.getStatus());
                return new Resultado(true, false);
            }
        }

        StatusOnboarding statusFinal;
        if (situacao.habilitaProgressao()) {
            kyb.atualizarDadosCadastrais(callback.razaoSocial(), callback.nomeFantasia());
            persistirRepresentantes(kyb.getId(), callback.representantes());
            statusFinal = StatusOnboarding.APROVADO;
        } else {
            statusFinal = StatusOnboarding.REPROVADO;
        }
        solicitacao.finalizar(statusFinal);

        kybRepository.save(kyb);
        solicitacaoRepository.save(solicitacao);
        eventPublisher.publishEvent(
                new KybFinalizadoEvent(solicitacaoId, solicitacao.getUsuarioId(), statusFinal, kyb.getId()));

        evento.marcarProcessado();
        log.info("Webhook KYB processado solicitacao={} status={}", solicitacaoId, statusFinal);
        return new Resultado(true, false);
    }

    private SituacaoCadastral mapearSituacao(String situacao) {
        if (situacao == null) return SituacaoCadastral.DESCONHECIDA;
        return switch (situacao.toUpperCase()) {
            case "ACTIVE", "ATIVA" -> SituacaoCadastral.ATIVA;
            case "SUSPENDED", "SUSPENSA" -> SituacaoCadastral.SUSPENSA;
            case "INAPT", "INAPTA" -> SituacaoCadastral.INAPTA;
            case "TERMINATED", "BAIXADA" -> SituacaoCadastral.BAIXADA;
            default -> SituacaoCadastral.DESCONHECIDA;
        };
    }

    private void persistirConsultaCnpj(
            UUID kybEmpresaId, SituacaoCadastral situacao, CelcoinKybCallbackRequest callback, String payloadCru) {
        ConsultaCNPJ consulta = ConsultaCNPJ.registrar(
                kybEmpresaId,
                situacao,
                callback.razaoSocial(),
                callback.nomeFantasia(),
                callback.cnaePrincipal(),
                callback.cnaesSecundarios(),
                callback.capitalSocial(),
                callback.dataAbertura(),
                payloadCru);
        consultaCnpjRepository.save(consulta);
    }

    private void persistirRepresentantes(
            UUID kybEmpresaId, java.util.List<CelcoinKybCallbackRequest.RepresentanteCallback> representantes) {
        if (representantes == null || representantes.isEmpty()) return;
        for (CelcoinKybCallbackRequest.RepresentanteCallback r : representantes) {
            try {
                Cpf cpfVo = new Cpf(r.cpf());
                representanteRepository.save(RepresentanteLegal.criar(kybEmpresaId, r.nome(), cpfVo, r.cargo()));
            } catch (IllegalArgumentException ignored) {
                // CPF invalido descartado; payload bruto em consulta_cnpj.payload_provider.
            }
        }
    }
}
