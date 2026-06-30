package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.dto.ProcessarCallbackConsentimentoCommand;
import com.dynamis.sep_api.credito.domain.exception.ConsentimentoInvalidoException;
import com.dynamis.sep_api.credito.domain.exception.ConsentimentoNaoEncontradoException;
import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.infrastructure.persistence.ConsentimentoOpenFinanceRepository;
import com.dynamis.sep_api.shared.application.usecase.RegistrarWebhookEventUseCase;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Processa webhook Celcoin Open Finance (Sprint 9 Task 9.5). Reusa outbox da Sprint 4
 * ({@link RegistrarWebhookEventUseCase}) pra idempotencia + auditoria.
 *
 * <p>Fluxo:
 *
 * <ol>
 *   <li>Persiste callback no outbox como {@code PENDENTE}. Duplicado por {@code Idempotency-Key}
 *       interrompe sem reprocessar.
 *   <li>Roteia por tipo:
 *       <ul>
 *         <li>{@code consent.authorized} / {@code consent.denied} / {@code consent.expired} ->
 *             {@link ProcessarCallbackConsentimentoUseCase} (Open Finance autoriza -> listener
 *             AFTER_COMMIT dispara consulta de movimentacao);
 *         <li>{@code movement.data} -> log INFO + marca PROCESSADO (movimentacao real e PULL
 *             via {@link ConsultarMovimentacaoOpenFinanceUseCase} pos autorizacao, nao push).
 *       </ul>
 *   <li>Tipo desconhecido -> marca FALHOU com motivo e retorna sem 4xx (provider pode evoluir
 *       payload sem quebrar producao).
 * </ol>
 */
@Service
public class ProcessarWebhookOpenFinanceUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarWebhookOpenFinanceUseCase.class);
    private static final String PROVIDER = "celcoin-open-finance";
    private static final String EVENT = "callback";

    private final RegistrarWebhookEventUseCase registrarWebhookEventUseCase;
    private final WebhookEventLogRepository webhookEventLogRepository;
    private final ConsentimentoOpenFinanceRepository consentimentoRepository;
    private final ProcessarCallbackConsentimentoUseCase processarCallback;

    public ProcessarWebhookOpenFinanceUseCase(
            RegistrarWebhookEventUseCase registrarWebhookEventUseCase,
            WebhookEventLogRepository webhookEventLogRepository,
            ConsentimentoOpenFinanceRepository consentimentoRepository,
            ProcessarCallbackConsentimentoUseCase processarCallback) {
        this.registrarWebhookEventUseCase = registrarWebhookEventUseCase;
        this.webhookEventLogRepository = webhookEventLogRepository;
        this.consentimentoRepository = consentimentoRepository;
        this.processarCallback = processarCallback;
    }

    public record Resultado(boolean aceito, boolean duplicado) {}

    public record CallbackOpenFinance(String tipo, String consentId, String motivo) {
        public boolean isAutorizacao() {
            return "consent.authorized".equalsIgnoreCase(tipo);
        }

        public boolean isNegacao() {
            return "consent.denied".equalsIgnoreCase(tipo) || "consent.expired".equalsIgnoreCase(tipo);
        }

        public boolean isMovimentacao() {
            return "movement.data".equalsIgnoreCase(tipo);
        }
    }

    @Transactional
    public Resultado executar(
            String idempotencyKey, String signature, String payloadCru, CallbackOpenFinance callback) {
        boolean gravado = registrarWebhookEventUseCase.executar(PROVIDER, EVENT, idempotencyKey, signature, payloadCru);
        if (!gravado) {
            log.info("Webhook Open Finance duplicado — sem reprocessamento");
            return new Resultado(true, true);
        }

        Optional<WebhookEventLog> eventoOpt = webhookEventLogRepository.findByIdempotencyKey(idempotencyKey);
        WebhookEventLog evento = eventoOpt.orElseThrow(() ->
                new IllegalStateException("WebhookEventLog ausente apos registrar — inconsistencia transacional"));

        if (callback == null
                || callback.consentId() == null
                || callback.consentId().isBlank()) {
            evento.marcarFalhou("consent_id ausente no payload");
            log.warn("Webhook Open Finance sem consent_id");
            return new Resultado(true, false);
        }

        if (callback.isAutorizacao() || callback.isNegacao()) {
            try {
                processarCallback.executar(
                        new ProcessarCallbackConsentimentoCommand(callback.consentId(), callback.isAutorizacao()));
                evento.marcarProcessado();
            } catch (ConsentimentoNaoEncontradoException ex) {
                evento.marcarFalhou("consentimento nao encontrado para consent_id");
                log.warn("Webhook Open Finance consent_id={} sem consentimento correspondente", callback.consentId());
            } catch (ConsentimentoInvalidoException ex) {
                // Sprint 9 fix code review Task 9.5: transicao invalida (ex.: dominio rejeita
                // estado-alvo) nao deve estourar 500 — outbox precisa persistir pra idempotency
                // funcionar em retry.
                evento.marcarFalhou("transicao invalida: " + ex.getMessage());
                log.warn(
                        "Webhook Open Finance consent_id={} transicao invalida: {}",
                        callback.consentId(),
                        ex.getMessage());
            }
            return new Resultado(true, false);
        }

        if (callback.isMovimentacao()) {
            // Movimentacao real e PULL via OpenFinanceProvider.consultarMovimentacao (disparado
            // pelo listener AFTER_COMMIT do autorizado). Push de movimentacao via webhook nao
            // dispara consulta — apenas ack pra outbox.
            Optional<ConsentimentoOpenFinance> consentimento =
                    consentimentoRepository.findByIdExternoCelcoin(callback.consentId());
            if (consentimento.isEmpty()) {
                evento.marcarFalhou("consentimento nao encontrado para movement.data");
                log.warn("Webhook Open Finance movement.data consent_id={} sem consentimento", callback.consentId());
            } else {
                evento.marcarProcessado();
                log.info(
                        "Webhook Open Finance movement.data recebido consent_id={} — consulta sera feita via PULL",
                        callback.consentId());
            }
            return new Resultado(true, false);
        }

        // Tipo desconhecido — payload evolui no provider; nao quebrar producao.
        evento.marcarFalhou("tipo de callback desconhecido: " + callback.tipo());
        log.warn("Webhook Open Finance tipo desconhecido tipo={}", callback.tipo());
        return new Resultado(true, false);
    }
}
