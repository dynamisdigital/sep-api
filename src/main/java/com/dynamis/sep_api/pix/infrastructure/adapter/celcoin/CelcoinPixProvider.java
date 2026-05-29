package com.dynamis.sep_api.pix.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.EventoWebhookPixNormalizado;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.StatusTransferenciaPixProvider;
import com.dynamis.sep_api.pix.infrastructure.adapter.PixWebhookNormalizer;
import com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto.CelcoinPixTransferRequest;
import com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto.CelcoinPixTransferResponse;
import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import com.dynamis.sep_api.shared.integration.RestClientFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adapter HTTP real (skeleton) do {@link PixProvider} para a Celcoin Pix (Epic 15 / Sprint 19).
 * Selecionado quando {@code app.pix.provider=celcoin}.
 *
 * <p>Autenticacao OAuth2 client-credentials via {@link CelcoinPixOAuthTokenProvider} (bloco
 * {@code app.celcoin.pix.*}). Resilience4j (instance {@code celcoin-pix}): retry em 5xx/IOException
 * + circuit breaker. Idempotency-Key e lida do MDC pelo {@code IdempotencyKeyInterceptor} — o
 * adapter popula o MDC a partir do {@code idempotencyKey} recebido antes da chamada com efeito
 * externo.
 *
 * <p><strong>Sem desembolso real nesta sprint</strong>: o contrato existe e e testavel por WireMock,
 * mas o use case de desembolso fica para as Sprints 20/21. Logs nunca incluem chave Pix, payload
 * bruto ou body de erro — apenas status HTTP, correlationId e ids tecnicos.
 */
@Component
@ConditionalOnProperty(name = "app.pix.provider", havingValue = "celcoin")
public class CelcoinPixProvider implements PixProvider {

    private static final Logger log = LoggerFactory.getLogger(CelcoinPixProvider.class);
    private static final String RESILIENCE_INSTANCE = "celcoin-pix";

    private final RestClient restClient;
    private final CelcoinPixOAuthTokenProvider tokenProvider;
    private final PixWebhookNormalizer webhookNormalizer;

    public CelcoinPixProvider(
            RestClientFactory factory,
            CelcoinPixProperties properties,
            CelcoinPixOAuthTokenProvider tokenProvider,
            PixWebhookNormalizer webhookNormalizer) {
        this.restClient = factory.forProvider(RESILIENCE_INSTANCE, properties.baseUrl());
        this.tokenProvider = tokenProvider;
        this.webhookNormalizer = webhookNormalizer;
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaTransferenciaPix solicitarTransferencia(
            ComandoTransferenciaPix comando, String idempotencyKey, String correlationId) {
        CelcoinPixTransferRequest payload =
                new CelcoinPixTransferRequest(comando.valor(), comando.chavePixDestino(), comando.descricao());
        try (MDCBridge ignored = MDCBridge.set(correlationId, idempotencyKey)) {
            CelcoinPixTransferResponse response = restClient
                    .post()
                    .uri("/pix/transfers")
                    .headers(this::headersAutenticacao)
                    .body(payload)
                    .retrieve()
                    .body(CelcoinPixTransferResponse.class);
            CelcoinPixTransferResponse validada = exigirResposta(response);
            log.info("Celcoin Pix solicitarTransferencia transfer_id={} status={}", validada.transferId(), validada.status());
            return new RespostaTransferenciaPix(validada.transferId(), mapearStatus(validada.status()));
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Celcoin Pix solicitarTransferencia falhou status={} correlationId={}",
                    ex.getStatusCode().value(),
                    correlationId);
            throw ex;
        }
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaTransferenciaPix consultarTransferencia(String externalId, String correlationId) {
        try (MDCBridge ignored = MDCBridge.set(correlationId, null)) {
            CelcoinPixTransferResponse response = restClient
                    .get()
                    .uri("/pix/transfers/{id}", externalId)
                    .headers(this::headersAutenticacao)
                    .retrieve()
                    .body(CelcoinPixTransferResponse.class);
            CelcoinPixTransferResponse validada = exigirResposta(response);
            return new RespostaTransferenciaPix(validada.transferId(), mapearStatus(validada.status()));
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Celcoin Pix consultarTransferencia falhou status={} correlationId={}",
                    ex.getStatusCode().value(),
                    correlationId);
            throw ex;
        }
    }

    @Override
    public EventoWebhookPixNormalizado normalizarWebhook(String payloadBruto) {
        return webhookNormalizer.normalizar(payloadBruto);
    }

    private CelcoinPixTransferResponse exigirResposta(CelcoinPixTransferResponse response) {
        if (response == null) {
            throw new IllegalStateException("Resposta nula do Celcoin Pix (esperado transfer_id + status)");
        }
        if (response.transferId() == null || response.transferId().isBlank()) {
            throw new IllegalStateException("Celcoin Pix sem transfer_id na resposta");
        }
        if (response.status() == null || response.status().isBlank()) {
            throw new IllegalStateException("Celcoin Pix sem status na resposta");
        }
        return response;
    }

    private StatusTransferenciaPixProvider mapearStatus(String statusCru) {
        return switch (statusCru.toUpperCase()) {
            case "PENDING", "CREATED" -> StatusTransferenciaPixProvider.PENDENTE;
            case "PROCESSING", "IN_PROGRESS" -> StatusTransferenciaPixProvider.PROCESSANDO;
            case "COMPLETED", "SETTLED", "CONFIRMED" -> StatusTransferenciaPixProvider.CONCLUIDA;
            case "REJECTED", "FAILED", "CANCELLED" -> StatusTransferenciaPixProvider.REJEITADA;
            default -> throw new IllegalStateException("Status Celcoin Pix desconhecido: " + statusCru);
        };
    }

    private void headersAutenticacao(HttpHeaders headers) {
        headers.setBearerAuth(tokenProvider.accessToken());
    }

    /**
     * AutoCloseable que popula MDC com {@code correlationId} e (opcional) {@code idempotencyKey},
     * restaurando o estado anterior ao fim. O {@code IdempotencyKeyInterceptor} le ambos do MDC.
     */
    private static final class MDCBridge implements AutoCloseable {

        private final String correlationAnterior;
        private final String idempotencyAnterior;

        private MDCBridge(String correlationAnterior, String idempotencyAnterior) {
            this.correlationAnterior = correlationAnterior;
            this.idempotencyAnterior = idempotencyAnterior;
        }

        static MDCBridge set(String correlationId, String idempotencyKey) {
            String correlationAnterior = MDC.get(CorrelationIdFilter.MDC_KEY);
            String idempotencyAnterior = MDC.get("idempotencyKey");
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
            }
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                MDC.put("idempotencyKey", idempotencyKey);
            }
            return new MDCBridge(correlationAnterior, idempotencyAnterior);
        }

        @Override
        public void close() {
            restaurar(CorrelationIdFilter.MDC_KEY, correlationAnterior);
            restaurar("idempotencyKey", idempotencyAnterior);
        }

        private static void restaurar(String chave, String anterior) {
            if (anterior == null) {
                MDC.remove(chave);
            } else {
                MDC.put(chave, anterior);
            }
        }
    }
}
