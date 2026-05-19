package com.dynamis.sep_api.credito.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.credito.application.port.out.OpenFinanceProvider;
import com.dynamis.sep_api.credito.application.port.out.dto.MovimentacaoConsolidada;
import com.dynamis.sep_api.credito.application.port.out.dto.RequisicaoConsentimento;
import com.dynamis.sep_api.credito.application.port.out.dto.RespostaConsentimento;
import com.dynamis.sep_api.credito.infrastructure.adapter.celcoin.dto.CelcoinOpenFinanceConsentRequest;
import com.dynamis.sep_api.credito.infrastructure.adapter.celcoin.dto.CelcoinOpenFinanceConsentResponse;
import com.dynamis.sep_api.credito.infrastructure.adapter.celcoin.dto.CelcoinOpenFinanceMovimentacaoResponse;
import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import com.dynamis.sep_api.shared.integration.RestClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Adapter HTTP real pro Celcoin/Finansystech Open Finance (Sprint 9). Selecionado quando
 * {@code app.open-finance.provider=celcoin}.
 *
 * <p>Autenticacao: OAuth2 client-credentials via {@link CelcoinOpenFinanceOAuthTokenProvider}
 * (bloco isolado {@code app.celcoin.open-finance.*}). Header {@code Authorization: Bearer <token>}.
 *
 * <p>Resilience4j (instance {@code celcoin-open-finance}): retry em 5xx + IOException com circuit
 * breaker.
 *
 * <p>LGPD: logs nunca incluem payload bruto nem body de erro do provider — apenas status HTTP,
 * correlationId e identificadores tecnicos. O {@code payloadConsolidado} persistido vem da
 * resposta JSON ja sanitizada pelo provider (snapshot agregado, NUNCA extrato transacional).
 */
@Component
@ConditionalOnProperty(name = "app.open-finance.provider", havingValue = "celcoin")
public class CelcoinOpenFinanceProvider implements OpenFinanceProvider {

    private static final Logger log = LoggerFactory.getLogger(CelcoinOpenFinanceProvider.class);
    private static final String RESILIENCE_INSTANCE = "celcoin-open-finance";

    private final RestClient restClient;
    private final CelcoinOpenFinanceMapper mapper;
    private final CelcoinOpenFinanceOAuthTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public CelcoinOpenFinanceProvider(
            RestClientFactory factory,
            CelcoinOpenFinanceMapper mapper,
            CelcoinOpenFinanceProperties properties,
            CelcoinOpenFinanceOAuthTokenProvider tokenProvider,
            ObjectMapper objectMapper) {
        this.restClient = factory.forProvider(RESILIENCE_INSTANCE, properties.baseUrl());
        this.mapper = mapper;
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaConsentimento iniciarConsentimento(RequisicaoConsentimento requisicao, String correlationId) {
        CelcoinOpenFinanceConsentRequest payload = mapper.toCelcoinRequest(requisicao);
        try (MDCBridge ignored = MDCBridge.set(correlationId)) {
            CelcoinOpenFinanceConsentResponse response = restClient
                    .post()
                    .uri("/consents")
                    .headers(this::headersAutenticacao)
                    .body(payload)
                    .retrieve()
                    .body(CelcoinOpenFinanceConsentResponse.class);
            log.info(
                    "Celcoin Open Finance iniciarConsentimento propostaId={} consent_id={}",
                    requisicao.propostaId(),
                    response != null ? response.idConsentimento() : "null");
            return mapper.toRespostaConsentimento(response);
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Celcoin Open Finance iniciarConsentimento falhou status={} correlationId={}",
                    ex.getStatusCode().value(),
                    correlationId);
            throw ex;
        }
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public MovimentacaoConsolidada consultarMovimentacao(String idExternoConsentimento, String correlationId) {
        try (MDCBridge ignored = MDCBridge.set(correlationId)) {
            CelcoinOpenFinanceMovimentacaoResponse response = restClient
                    .get()
                    .uri("/consents/{id}/movements", idExternoConsentimento)
                    .headers(this::headersAutenticacao)
                    .retrieve()
                    .body(CelcoinOpenFinanceMovimentacaoResponse.class);
            String payloadCru = serializar(response);
            log.info(
                    "Celcoin Open Finance consultarMovimentacao idExterno={} meses={}",
                    idExternoConsentimento,
                    response != null ? response.mesesAvaliados() : "null");
            return mapper.toMovimentacaoConsolidada(response, payloadCru);
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Celcoin Open Finance consultarMovimentacao falhou status={} correlationId={}",
                    ex.getStatusCode().value(),
                    correlationId);
            throw ex;
        }
    }

    private void headersAutenticacao(HttpHeaders headers) {
        headers.setBearerAuth(tokenProvider.accessToken());
    }

    private String serializar(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Falha ao serializar payload Celcoin Open Finance para persistencia");
            return "{}";
        }
    }

    /**
     * AutoCloseable que popula MDC apenas com {@code correlationId} e restaura ao fim. Caller
     * (use case) gerencia {@code idempotencyKey} no MDC quando aplicavel.
     */
    private static final class MDCBridge implements AutoCloseable {

        private final String correlationAnterior;

        private MDCBridge(String correlationAnterior) {
            this.correlationAnterior = correlationAnterior;
        }

        static MDCBridge set(String correlationId) {
            String anterior = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
            }
            return new MDCBridge(anterior);
        }

        @Override
        public void close() {
            if (correlationAnterior == null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            } else {
                MDC.put(CorrelationIdFilter.MDC_KEY, correlationAnterior);
            }
        }
    }
}
