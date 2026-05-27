package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.usecase.ProcessarCallbackAssinaturaUseCase.CallbackAssinatura;
import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
import com.dynamis.sep_api.contratos.web.dto.AssinaturaCallbackClicksign;
import com.dynamis.sep_api.shared.application.usecase.RegistrarWebhookEventUseCase;
import com.dynamis.sep_api.shared.domain.model.WebhookEventLog;
import com.dynamis.sep_api.shared.infrastructure.persistence.WebhookEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Orquestra recebimento de webhook de assinatura digital (Sprint 11 Task 11.6). Reusa padrao
 * Sprint 4/6/7/9: outbox via {@link RegistrarWebhookEventUseCase} pra idempotencia + auditoria,
 * roteamento para {@link ProcessarCallbackAssinaturaUseCase} apos persistir evento.
 *
 * <p>Fluxo:
 * <ol>
 *   <li>Registra evento no outbox como {@code PENDENTE}. Duplicado por {@code idempotencyKey}
 *       (derivado do payload no controller) interrompe sem reprocessar.
 *   <li>Mapeia {@code event.name} Clicksign para {@link StatusEnvelope} (case-insensitive).
 *   <li>Invoca {@link ProcessarCallbackAssinaturaUseCase#executar} com {@link CallbackAssinatura}.
 *   <li>Marca outbox {@code PROCESSADO} (ou {@code FALHOU} em estado invalido).
 * </ol>
 *
 * <p>Tipo desconhecido nao causa 4xx — provider pode evoluir payload. Marca outbox como
 * {@code FALHOU} com motivo + log warn (mesmo padrao do {@code ProcessarWebhookOpenFinanceUseCase}).
 */
@Service
public class ProcessarWebhookAssinaturaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarWebhookAssinaturaUseCase.class);
    private static final String EVENT = "callback";

    private final RegistrarWebhookEventUseCase registrarWebhookEventUseCase;
    private final WebhookEventLogRepository webhookEventLogRepository;
    private final ProcessarCallbackAssinaturaUseCase processarCallback;

    public ProcessarWebhookAssinaturaUseCase(
            RegistrarWebhookEventUseCase registrarWebhookEventUseCase,
            WebhookEventLogRepository webhookEventLogRepository,
            ProcessarCallbackAssinaturaUseCase processarCallback) {
        this.registrarWebhookEventUseCase = registrarWebhookEventUseCase;
        this.webhookEventLogRepository = webhookEventLogRepository;
        this.processarCallback = processarCallback;
    }

    public record Resultado(boolean aceito, boolean duplicado) {}

    @Transactional
    public Resultado executar(
            String provider,
            String idempotencyKey,
            String idEventoExterno,
            String signature,
            String payloadCru,
            AssinaturaCallbackClicksign callback) {

        String providerOutbox = provider + "-assinatura";
        boolean gravado =
                registrarWebhookEventUseCase.executar(providerOutbox, EVENT, idempotencyKey, signature, payloadCru);
        if (!gravado) {
            log.info("Webhook assinatura duplicado idempotencyKey={} — sem reprocessamento", idempotencyKey);
            return new Resultado(true, true);
        }

        Optional<WebhookEventLog> eventoOpt = webhookEventLogRepository.findByIdempotencyKey(idempotencyKey);
        WebhookEventLog evento = eventoOpt.orElseThrow(() ->
                new IllegalStateException("WebhookEventLog ausente apos registrar — inconsistencia transacional"));

        String eventName =
                callback != null && callback.event() != null ? callback.event().name() : null;
        String documentKey = callback != null && callback.document() != null
                ? callback.document().key()
                : null;
        OffsetDateTime occurredAt =
                callback != null && callback.event() != null ? callback.event().occurredAt() : null;

        if (isBlank(documentKey)) {
            evento.marcarFalhou("document.key ausente no payload");
            log.warn("Webhook assinatura sem document.key idempotencyKey={}", idempotencyKey);
            return new Resultado(true, false);
        }
        if (isBlank(eventName)) {
            evento.marcarFalhou("event.name ausente no payload");
            log.warn("Webhook assinatura sem event.name idempotencyKey={} documentKey={}", idempotencyKey, documentKey);
            return new Resultado(true, false);
        }

        StatusEnvelope status = mapearEvento(eventName);
        if (status == null) {
            evento.marcarFalhou("event.name desconhecido: " + eventName);
            log.warn(
                    "Webhook assinatura event.name desconhecido idempotencyKey={} eventName={}",
                    idempotencyKey,
                    eventName);
            return new Resultado(true, false);
        }

        OffsetDateTime dataEvento;
        if (occurredAt != null) {
            dataEvento = occurredAt;
        } else {
            dataEvento = OffsetDateTime.now();
            log.warn(
                    "Webhook assinatura sem event.occurred_at — fallback OffsetDateTime.now() idempotencyKey={} documentKey={} eventName={}",
                    idempotencyKey,
                    documentKey,
                    eventName);
        }
        try {
            processarCallback.executar(new CallbackAssinatura(
                    provider, documentKey, idEventoExterno, status, truncarPayload(payloadCru), null, dataEvento));
            evento.marcarProcessado();
        } catch (ContratoEstadoInvalidoException ex) {
            evento.marcarFalhou("estado invalido: " + ex.getMessage());
            log.warn(
                    "Webhook assinatura estado invalido idempotencyKey={} documentKey={}: {}",
                    idempotencyKey,
                    documentKey,
                    ex.getMessage());
        }
        return new Resultado(true, false);
    }

    /**
     * Mapeia {@code event.name} do Clicksign para {@link StatusEnvelope}. Comparacao
     * case-insensitive. Retorna {@code null} para eventos nao reconhecidos — caller marca outbox
     * {@code FALHOU} sem 4xx.
     *
     * <p>Mapping baseado em eventos publicos do Clicksign:
     * <ul>
     *   <li>{@code upload}, {@code add_signer} → informativos (ignorados aqui — envelope ja existe)
     *   <li>{@code view} → {@code VISUALIZADO}
     *   <li>{@code sign}, {@code auto_close} → {@code ASSINADO}
     *   <li>{@code refuse} → {@code RECUSADO}
     *   <li>{@code deadline}, {@code cancel} → {@code EXPIRADO}
     * </ul>
     */
    private StatusEnvelope mapearEvento(String eventName) {
        return switch (eventName.toLowerCase(Locale.ROOT)) {
            case "view" -> StatusEnvelope.VISUALIZADO;
            case "sign", "auto_close" -> StatusEnvelope.ASSINADO;
            case "refuse" -> StatusEnvelope.RECUSADO;
            case "deadline", "cancel" -> StatusEnvelope.EXPIRADO;
            case "upload", "add_signer" -> StatusEnvelope.ENVIADO;
            default -> null;
        };
    }

    private static final int PAYLOAD_RESUMO_MAX = 1000;

    /**
     * Trunca payload em {@value #PAYLOAD_RESUMO_MAX} chars pra evitar inflar o audit. Sprint 15
     * — 15F-017: quando trunca, append sufixo `...[TRUNCATED:<len_original>]` pra preservar
     * trilha auditavel do tamanho original. Mantem retro-compat: payloads <= max nao mudam.
     */
    private static String truncarPayload(String payload) {
        if (payload == null) {
            return null;
        }
        if (payload.length() <= PAYLOAD_RESUMO_MAX) {
            return payload;
        }
        return payload.substring(0, PAYLOAD_RESUMO_MAX) + "...[TRUNCATED:" + payload.length() + "]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
