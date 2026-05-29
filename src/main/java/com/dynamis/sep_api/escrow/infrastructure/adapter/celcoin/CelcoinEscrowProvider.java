package com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.escrow.application.port.out.EscrowProvider;
import com.dynamis.sep_api.escrow.application.port.out.dto.ComandoCriarContaEscrow;
import com.dynamis.sep_api.escrow.application.port.out.dto.ComandoCriarWallet;
import com.dynamis.sep_api.escrow.application.port.out.dto.RespostaContaEscrow;
import com.dynamis.sep_api.escrow.application.port.out.dto.RespostaWallet;
import com.dynamis.sep_api.escrow.application.port.out.exception.EscrowProviderException;
import com.dynamis.sep_api.escrow.application.port.out.exception.EscrowProviderHttpException;
import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin.dto.CelcoinContaEscrowRequest;
import com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin.dto.CelcoinContaEscrowResponse;
import com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin.dto.CelcoinWalletRequest;
import com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin.dto.CelcoinWalletResponse;
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

import java.math.BigDecimal;

/**
 * Adapter HTTP real do {@link EscrowProvider} para a Celcoin (Epic 15 / Sprint 19). Selecionado
 * quando {@code app.escrow.provider=celcoin}.
 *
 * <p>OAuth2 client-credentials via {@link CelcoinEscrowOAuthTokenProvider} ({@code
 * app.celcoin.escrow.*}). Resilience4j (instance {@code celcoin-escrow}): retry em 5xx/IOException +
 * circuit breaker. Idempotency-Key lida do MDC pelo {@code IdempotencyKeyInterceptor}.
 *
 * <p>Nao substitui o {@code RegistrarMovimentacaoEscrowUseCase} local (Sprint 12): cobre apenas a
 * fronteira com o provedor. Logs sem dados sensiveis — apenas status HTTP, correlationId e ids.
 */
@Component
@ConditionalOnProperty(name = "app.escrow.provider", havingValue = "celcoin")
public class CelcoinEscrowProvider implements EscrowProvider {

    private static final Logger log = LoggerFactory.getLogger(CelcoinEscrowProvider.class);
    private static final String RESILIENCE_INSTANCE = "celcoin-escrow";

    private final RestClient restClient;
    private final CelcoinEscrowOAuthTokenProvider tokenProvider;

    public CelcoinEscrowProvider(
            RestClientFactory factory,
            CelcoinEscrowProperties properties,
            CelcoinEscrowOAuthTokenProvider tokenProvider) {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new IllegalStateException(
                    "app.celcoin.escrow.base-url obrigatorio quando app.escrow.provider=celcoin");
        }
        this.restClient = factory.forProvider(RESILIENCE_INSTANCE, properties.baseUrl());
        this.tokenProvider = tokenProvider;
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaContaEscrow criarContaEscrow(
            ComandoCriarContaEscrow comando, String idempotencyKey, String correlationId) {
        try (MDCBridge ignored = MDCBridge.set(correlationId, idempotencyKey)) {
            CelcoinContaEscrowResponse response = restClient
                    .post()
                    .uri("/escrow/accounts")
                    .headers(this::headersAutenticacao)
                    .body(new CelcoinContaEscrowRequest(comando.titular()))
                    .retrieve()
                    .body(CelcoinContaEscrowResponse.class);
            CelcoinContaEscrowResponse validada = exigirConta(response);
            log.info("Celcoin escrow criarContaEscrow account_id={} status={}", validada.accountId(), validada.status());
            return new RespostaContaEscrow(validada.accountId(), mapearStatusConta(validada.status()));
        } catch (RestClientResponseException ex) {
            throw traduzirHttp("criarContaEscrow", ex, correlationId);
        }
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaContaEscrow consultarContaEscrow(String externalId, String correlationId) {
        try (MDCBridge ignored = MDCBridge.set(correlationId, null)) {
            CelcoinContaEscrowResponse response = restClient
                    .get()
                    .uri("/escrow/accounts/{id}", externalId)
                    .headers(this::headersAutenticacao)
                    .retrieve()
                    .body(CelcoinContaEscrowResponse.class);
            CelcoinContaEscrowResponse validada = exigirConta(response);
            return new RespostaContaEscrow(validada.accountId(), mapearStatusConta(validada.status()));
        } catch (RestClientResponseException ex) {
            throw traduzirHttp("consultarContaEscrow", ex, correlationId);
        }
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaWallet criarWallet(ComandoCriarWallet comando, String idempotencyKey, String correlationId) {
        CelcoinWalletRequest payload = new CelcoinWalletRequest(
                comando.contaEscrowExternalId(),
                comando.propostaId() == null ? null : comando.propostaId().toString(),
                comando.tipoWallet().name());
        try (MDCBridge ignored = MDCBridge.set(correlationId, idempotencyKey)) {
            CelcoinWalletResponse response = restClient
                    .post()
                    .uri("/escrow/wallets")
                    .headers(this::headersAutenticacao)
                    .body(payload)
                    .retrieve()
                    .body(CelcoinWalletResponse.class);
            CelcoinWalletResponse validada = exigirWallet(response);
            log.info("Celcoin escrow criarWallet wallet_id={}", validada.walletId());
            return new RespostaWallet(validada.walletId(), validada.saldo());
        } catch (RestClientResponseException ex) {
            throw traduzirHttp("criarWallet", ex, correlationId);
        }
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaWallet consultarWallet(String externalId, String correlationId) {
        try (MDCBridge ignored = MDCBridge.set(correlationId, null)) {
            CelcoinWalletResponse response = restClient
                    .get()
                    .uri("/escrow/wallets/{id}", externalId)
                    .headers(this::headersAutenticacao)
                    .retrieve()
                    .body(CelcoinWalletResponse.class);
            CelcoinWalletResponse validada = exigirWallet(response);
            return new RespostaWallet(validada.walletId(), validada.saldo());
        } catch (RestClientResponseException ex) {
            throw traduzirHttp("consultarWallet", ex, correlationId);
        }
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public BigDecimal consultarSaldo(String walletExternalId, String correlationId) {
        try (MDCBridge ignored = MDCBridge.set(correlationId, null)) {
            CelcoinWalletResponse response = restClient
                    .get()
                    .uri("/escrow/wallets/{id}/balance", walletExternalId)
                    .headers(this::headersAutenticacao)
                    .retrieve()
                    .body(CelcoinWalletResponse.class);
            if (response == null || response.saldo() == null) {
                throw new EscrowProviderException("Celcoin escrow sem saldo na resposta de balance");
            }
            return response.saldo();
        } catch (RestClientResponseException ex) {
            throw traduzirHttp("consultarSaldo", ex, correlationId);
        }
    }

    private CelcoinContaEscrowResponse exigirConta(CelcoinContaEscrowResponse response) {
        if (response == null || response.accountId() == null || response.accountId().isBlank()) {
            throw new EscrowProviderException("Celcoin escrow sem account_id na resposta");
        }
        if (response.status() == null || response.status().isBlank()) {
            throw new EscrowProviderException("Celcoin escrow sem status na resposta de conta");
        }
        return response;
    }

    private CelcoinWalletResponse exigirWallet(CelcoinWalletResponse response) {
        if (response == null || response.walletId() == null || response.walletId().isBlank()) {
            throw new EscrowProviderException("Celcoin escrow sem wallet_id na resposta");
        }
        return response;
    }

    private StatusContaEscrow mapearStatusConta(String statusCru) {
        return switch (statusCru.toUpperCase()) {
            case "OPENING", "PENDING" -> StatusContaEscrow.EM_ABERTURA;
            case "ACTIVE", "OPEN" -> StatusContaEscrow.ATIVA;
            case "BLOCKED", "FROZEN" -> StatusContaEscrow.BLOQUEADA;
            case "CLOSED" -> StatusContaEscrow.ENCERRADA;
            default -> throw new EscrowProviderException("Status Celcoin escrow desconhecido: " + statusCru);
        };
    }

    /**
     * Traduz {@link RestClientResponseException} (4xx/5xx) para {@link EscrowProviderHttpException},
     * sem vazar o response cru para a camada de application. O log nao inclui body de erro.
     */
    private EscrowProviderHttpException traduzirHttp(String operacao, RestClientResponseException ex, String correlationId) {
        int status = ex.getStatusCode().value();
        log.warn("Celcoin escrow {} falhou status={} correlationId={}", operacao, status, correlationId);
        return new EscrowProviderHttpException(status, "Celcoin escrow HTTP " + status, ex);
    }

    private void headersAutenticacao(HttpHeaders headers) {
        headers.setBearerAuth(tokenProvider.accessToken());
    }

    /** Popula MDC com correlationId + idempotencyKey (opcional) e restaura ao fim. */
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
