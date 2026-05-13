package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.port.out.dto.ResultadoKycProvider;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.model.ResultadoVerificacao;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.CelcoinKycMapper;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKycResultadoResponse;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ResultadoVerificacaoRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.application.usecase.RegistrarWebhookEventUseCase;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Processa o callback assincrono de KYC vindo do {@code CelcoinKycWebhookController}. Reusa a
 * outbox da Sprint 4 ({@link RegistrarWebhookEventUseCase}) para idempotencia e auditoria.
 *
 * <p>Fluxo:
 *
 * <ol>
 *   <li>Persiste o callback no outbox como {@code PENDENTE}. Se o use case detectar duplicado
 *       (mesma {@code Idempotency-Key}), interrompe — chamada idempotente.
 *   <li>Localiza a solicitacao por {@code idVerificacaoExterna}. Se nao encontrar, marca o evento
 *       FALHOU (sem dados sensiveis na mensagem) e retorna — nao propaga 500.
 *   <li>Converte a resposta Celcoin em {@link ResultadoKycProvider} via {@link CelcoinKycMapper}.
 *   <li>Se {@code EmAndamento}, marca o evento PROCESSADO sem finalizar a solicitacao — caller
 *       (Celcoin) pode reenviar quando tiver status final.
 *   <li>Se {@code Finalizado}, persiste {@code ResultadoVerificacao}, transiciona a solicitacao
 *       via {@code finalizar(...)} e publica {@code OnboardingFinalizadoEvent}.
 * </ol>
 */
@Service
public class ProcessarCallbackKycUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarCallbackKycUseCase.class);
    private static final String PROVIDER = "celcoin-kyc";
    private static final String EVENT = "callback";

    private final RegistrarWebhookEventUseCase registrarWebhookEventUseCase;
    private final WebhookEventLogRepository webhookEventLogRepository;
    private final SolicitacaoOnboardingRepository solicitacaoRepository;
    private final ResultadoVerificacaoRepository resultadoRepository;
    private final CelcoinKycMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public ProcessarCallbackKycUseCase(
            RegistrarWebhookEventUseCase registrarWebhookEventUseCase,
            WebhookEventLogRepository webhookEventLogRepository,
            SolicitacaoOnboardingRepository solicitacaoRepository,
            ResultadoVerificacaoRepository resultadoRepository,
            CelcoinKycMapper mapper,
            ApplicationEventPublisher eventPublisher) {
        this.registrarWebhookEventUseCase = registrarWebhookEventUseCase;
        this.webhookEventLogRepository = webhookEventLogRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.resultadoRepository = resultadoRepository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Resultado da chamada — usado pelo controller para decidir o response. Sempre 202 quando
     * {@code aceito = true}, independente de duplicado ou novo.
     */
    public record Resultado(boolean aceito, boolean duplicado) {}

    @Transactional
    public Resultado executar(String idempotencyKey, String signature, String payloadCru, CelcoinKycCallback callback) {
        boolean gravado = registrarWebhookEventUseCase.executar(PROVIDER, EVENT, idempotencyKey, signature, payloadCru);
        if (!gravado) {
            log.info("Webhook KYC duplicado idempotencyKey={} — sem reprocessamento", idempotencyKey);
            return new Resultado(true, true);
        }

        Optional<WebhookEventLog> eventoOpt = webhookEventLogRepository.findByIdempotencyKey(idempotencyKey);
        WebhookEventLog evento = eventoOpt.orElseThrow(() ->
                new IllegalStateException("WebhookEventLog ausente apos registrar — inconsistencia transacional"));

        if (callback == null
                || callback.idVerificacao() == null
                || callback.idVerificacao().isBlank()) {
            evento.marcarFalhou("verification_id ausente no payload");
            log.warn("Webhook KYC sem verification_id idempotencyKey={}", idempotencyKey);
            return new Resultado(true, false);
        }

        Optional<SolicitacaoOnboarding> solicitacaoOpt =
                solicitacaoRepository.findByIdVerificacaoExterna(callback.idVerificacao());
        if (solicitacaoOpt.isEmpty()) {
            evento.marcarFalhou("solicitacao nao encontrada para verification_id");
            log.warn("Webhook KYC sem solicitacao correspondente idempotencyKey={}", idempotencyKey);
            return new Resultado(true, false);
        }
        SolicitacaoOnboarding solicitacao = solicitacaoOpt.get();

        CelcoinKycResultadoResponse resposta =
                new CelcoinKycResultadoResponse(callback.idVerificacao(), callback.status(), callback.reason());
        ResultadoKycProvider resultado = mapper.toResultadoKyc(resposta, payloadCru);

        if (resultado instanceof ResultadoKycProvider.EmAndamento) {
            log.info(
                    "Webhook KYC em andamento idempotencyKey={} verification_id={} — nao finaliza",
                    idempotencyKey,
                    callback.idVerificacao());
            evento.marcarProcessado();
            return new Resultado(true, false);
        }

        ResultadoKycProvider.Finalizado finalizado = (ResultadoKycProvider.Finalizado) resultado;
        ResultadoVerificacao novoResultado = ResultadoVerificacao.registrar(
                solicitacao.getId(), finalizado.statusFinal(), finalizado.motivo(), finalizado.payloadProvider());
        resultadoRepository.save(novoResultado);

        solicitacao.finalizar(finalizado.statusFinal());
        solicitacaoRepository.save(solicitacao);

        eventPublisher.publishEvent(new OnboardingFinalizadoEvent(
                solicitacao.getId(), solicitacao.getUsuarioId(), finalizado.statusFinal(), callback.idVerificacao()));

        evento.marcarProcessado();
        log.info(
                "Webhook KYC processado idempotencyKey={} solicitacao={} status={}",
                idempotencyKey,
                solicitacao.getId(),
                finalizado.statusFinal());
        return new Resultado(true, false);
    }

    /** Vista de leitura do payload do callback usado para roteamento interno; isola controller. */
    public record CelcoinKycCallback(String idVerificacao, String status, String reason) {}
}
