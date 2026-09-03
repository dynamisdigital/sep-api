package com.dynamis.sep_api.pix.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoCadastrarChavePix;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoCriarCobrancaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.EventoWebhookPixNormalizado;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaCadastroChavePix;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaCobrancaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.StatusTransferenciaPixProvider;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderException;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderHttpException;
import com.dynamis.sep_api.pix.infrastructure.adapter.PixWebhookNormalizer;
import com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto.CelcoinPixCobrancaRequest;
import com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto.CelcoinPixCobrancaResponse;
import com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto.CelcoinPixKeyRequest;
import com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto.CelcoinPixKeyResponse;
import com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto.CelcoinPixTransferRequest;
import com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.dto.CelcoinPixTransferResponse;
import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import com.dynamis.sep_api.shared.integration.IdempotencyKeyInterceptor;
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
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new IllegalStateException("app.celcoin.pix.base-url obrigatorio quando app.pix.provider=celcoin");
        }
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
            log.info(
                    "Celcoin Pix solicitarTransferencia transfer_id={} status={}",
                    validada.transferId(),
                    validada.status());
            return new RespostaTransferenciaPix(validada.transferId(), mapearStatus(validada.status()));
        } catch (RestClientResponseException ex) {
            throw traduzirHttp("solicitarTransferencia", ex, correlationId);
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
            throw traduzirHttp("consultarTransferencia", ex, correlationId);
        }
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaCobrancaPix criarCobrancaRecebimento(ComandoCriarCobrancaPix comando, String correlationId) {
        CelcoinPixCobrancaRequest payload = new CelcoinPixCobrancaRequest(comando.txid(), comando.valor());
        try (MDCBridge ignored = MDCBridge.set(correlationId, comando.txid())) {
            CelcoinPixCobrancaResponse response = restClient
                    .post()
                    .uri("/pix/charges")
                    .headers(this::headersAutenticacao)
                    .body(payload)
                    .retrieve()
                    .body(CelcoinPixCobrancaResponse.class);
            CelcoinPixCobrancaResponse validada = exigirCobranca(response);
            log.info("Celcoin Pix criarCobrancaRecebimento txid={} charge_id={}", validada.txid(), validada.chargeId());
            return new RespostaCobrancaPix(validada.txid(), validada.chargeId(), validada.copiaCola());
        } catch (RestClientResponseException ex) {
            throw traduzirHttp("criarCobrancaRecebimento", ex, correlationId);
        }
    }

    @Override
    public EventoWebhookPixNormalizado normalizarWebhook(String payloadBruto) {
        return webhookNormalizer.normalizar(payloadBruto);
    }

    /**
     * Cadastro de chave (Sprint 31). Contrato {@code POST /pix/keys} e <strong>skeleton local da
     * Fase 4</strong> — validar contra a documentacao real na Fase 5. A chave em claro trafega
     * apenas no body HTTP em memoria; logs levam somente {@code key_id}.
     */
    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaCadastroChavePix cadastrarChave(
            ComandoCadastrarChavePix comando, String idempotencyKey, String correlationId) {
        CelcoinPixKeyRequest payload =
                new CelcoinPixKeyRequest(comando.tipo().name(), comando.valorNormalizado(), comando.contaTecnicaId());
        try (MDCBridge ignored = MDCBridge.set(correlationId, idempotencyKey)) {
            CelcoinPixKeyResponse response = restClient
                    .post()
                    .uri("/pix/keys")
                    .headers(this::headersAutenticacao)
                    .body(payload)
                    .retrieve()
                    .body(CelcoinPixKeyResponse.class);
            CelcoinPixKeyResponse validada = exigirChave(response);
            log.info("Celcoin Pix cadastrarChave key_id={}", validada.keyId());
            return new RespostaCadastroChavePix(validada.keyId());
        } catch (RestClientResponseException ex) {
            throw traduzirHttp("cadastrarChave", ex, correlationId);
        }
    }

    /**
     * Remocao de chave pelo identificador tecnico (Sprint 31). {@code 404} do provider e tratado
     * como sucesso idempotente (contrato skeleton) e nao reentra em retry; demais erros seguem a
     * traducao sanitizada padrao.
     */
    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public void removerChave(String providerKeyId, String correlationId) {
        try (MDCBridge ignored = MDCBridge.set(correlationId, null)) {
            restClient
                    .delete()
                    .uri("/pix/keys/{id}", providerKeyId)
                    .headers(this::headersAutenticacao)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Celcoin Pix removerChave key_id={} removida", providerKeyId);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.info("Celcoin Pix removerChave key_id={} inexistente no provider (idempotente)", providerKeyId);
                return;
            }
            throw traduzirHttp("removerChave", ex, correlationId);
        }
    }

    private CelcoinPixKeyResponse exigirChave(CelcoinPixKeyResponse response) {
        if (response == null || response.keyId() == null || response.keyId().isBlank()) {
            throw new PixProviderException("Celcoin Pix sem key_id na resposta de cadastro de chave");
        }
        return response;
    }

    private CelcoinPixCobrancaResponse exigirCobranca(CelcoinPixCobrancaResponse response) {
        if (response == null) {
            throw new PixProviderException("Resposta nula do Celcoin Pix (esperado txid + charge_id)");
        }
        if (response.txid() == null || response.txid().isBlank()) {
            throw new PixProviderException("Celcoin Pix sem txid na resposta de cobranca");
        }
        if (response.chargeId() == null || response.chargeId().isBlank()) {
            throw new PixProviderException("Celcoin Pix sem charge_id na resposta de cobranca");
        }
        return response;
    }

    private CelcoinPixTransferResponse exigirResposta(CelcoinPixTransferResponse response) {
        if (response == null) {
            throw new PixProviderException("Resposta nula do Celcoin Pix (esperado transfer_id + status)");
        }
        if (response.transferId() == null || response.transferId().isBlank()) {
            throw new PixProviderException("Celcoin Pix sem transfer_id na resposta");
        }
        if (response.status() == null || response.status().isBlank()) {
            throw new PixProviderException("Celcoin Pix sem status na resposta");
        }
        return response;
    }

    private StatusTransferenciaPixProvider mapearStatus(String statusCru) {
        return switch (statusCru.toUpperCase()) {
            case "PENDING", "CREATED" -> StatusTransferenciaPixProvider.PENDENTE;
            case "PROCESSING", "IN_PROGRESS" -> StatusTransferenciaPixProvider.PROCESSANDO;
            case "COMPLETED", "SETTLED", "CONFIRMED" -> StatusTransferenciaPixProvider.CONCLUIDA;
            case "REJECTED", "FAILED", "CANCELLED" -> StatusTransferenciaPixProvider.REJEITADA;
            default -> throw new PixProviderException("Status Celcoin Pix desconhecido: " + statusCru);
        };
    }

    /**
     * Traduz {@link RestClientResponseException} (4xx/5xx) para {@link PixProviderHttpException},
     * sem vazar o response cru para a camada de application. O log nao inclui body de erro.
     */
    private PixProviderHttpException traduzirHttp(
            String operacao, RestClientResponseException ex, String correlationId) {
        int status = ex.getStatusCode().value();
        log.warn("Celcoin Pix {} falhou status={} correlationId={}", operacao, status, correlationId);
        return new PixProviderHttpException(status, "Celcoin Pix HTTP " + status, ex);
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
            String idempotencyAnterior = MDC.get(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
            }
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                MDC.put(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY, idempotencyKey);
            }
            return new MDCBridge(correlationAnterior, idempotencyAnterior);
        }

        @Override
        public void close() {
            restaurar(CorrelationIdFilter.MDC_KEY, correlationAnterior);
            restaurar(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY, idempotencyAnterior);
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
