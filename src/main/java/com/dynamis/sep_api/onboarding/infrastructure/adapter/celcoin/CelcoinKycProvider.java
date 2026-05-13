package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.KycProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoVerificacaoKyc;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaInicioVerificacao;
import com.dynamis.sep_api.onboarding.application.port.out.dto.ResultadoKycProvider;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKycRequest;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKycResponse;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKycResultadoResponse;
import com.dynamis.sep_api.shared.integration.IdempotencyKeyInterceptor;
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
 * Adapter HTTP real para o Celcoin Onboarding KYC PF. Selecionado quando {@code
 * app.kyc.provider=celcoin}.
 *
 * <p>Resilience4j (instance {@code celcoin-kyc}): retry 3x backoff exponencial + circuit breaker.
 * Timeout via {@link RestClientFactory} ({@code app.integration.read-timeout-seconds=30}).
 *
 * <p>Autenticacao OAuth2 client-credentials NAO esta implementada nesta sprint — pendente quando
 * credenciais reais Celcoin estiverem disponiveis (TODO Sprint 7+). Por enquanto usa basic
 * client-id/secret como header static, util pra WireMock IT.
 */
@Component
@ConditionalOnProperty(name = "app.kyc.provider", havingValue = "celcoin")
public class CelcoinKycProvider implements KycProvider {

    private static final Logger log = LoggerFactory.getLogger(CelcoinKycProvider.class);
    private static final String RESILIENCE_INSTANCE = "celcoin-kyc";

    private final RestClient restClient;
    private final CelcoinKycMapper mapper;
    private final CelcoinKycProperties properties;
    private final ObjectMapper objectMapper;

    public CelcoinKycProvider(
            RestClientFactory factory,
            CelcoinKycMapper mapper,
            CelcoinKycProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = factory.forProvider(RESILIENCE_INSTANCE, properties.baseUrl());
        this.mapper = mapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaInicioVerificacao iniciarVerificacao(RequisicaoVerificacaoKyc requisicao, String correlationId) {
        CelcoinKycRequest payload = mapper.toCelcoinRequest(requisicao);
        try (MDCBridge ignored =
                MDCBridge.set(correlationId, requisicao.solicitacaoId().toString())) {
            CelcoinKycResponse response = restClient
                    .post()
                    .uri("/verifications")
                    .headers(this::headersAutenticacao)
                    .body(payload)
                    .retrieve()
                    .body(CelcoinKycResponse.class);
            log.info(
                    "Celcoin KYC iniciada solicitacaoId={} verificationId={}",
                    requisicao.solicitacaoId(),
                    response != null ? response.idVerificacao() : "null");
            return mapper.toRespostaInicio(response);
        } catch (RestClientResponseException ex) {
            log.warn("Celcoin KYC iniciar falhou status={} body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ex;
        }
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public ResultadoKycProvider consultarResultado(String idVerificacaoExterna, String correlationId) {
        try (MDCBridge ignored = MDCBridge.set(correlationId, null)) {
            CelcoinKycResultadoResponse response = restClient
                    .get()
                    .uri("/verifications/{id}", idVerificacaoExterna)
                    .headers(this::headersAutenticacao)
                    .retrieve()
                    .body(CelcoinKycResultadoResponse.class);
            String payloadCru = serializar(response);
            return mapper.toResultadoKyc(response, payloadCru);
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Celcoin KYC consultar falhou status={} body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ex;
        }
    }

    private void headersAutenticacao(HttpHeaders headers) {
        if (properties.clientId() != null && !properties.clientId().isBlank()) {
            headers.add("X-Celcoin-Client-Id", properties.clientId());
        }
        if (properties.clientSecret() != null && !properties.clientSecret().isBlank()) {
            headers.add("X-Celcoin-Client-Secret", properties.clientSecret());
        }
    }

    private String serializar(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Falha ao serializar payload Celcoin: {}", e.getMessage());
            return "{}";
        }
    }

    /** AutoCloseable que popula MDC com correlation/idempotency e limpa ao fim. */
    private static final class MDCBridge implements AutoCloseable {
        static MDCBridge set(String correlationId, String idempotencyKey) {
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put(com.dynamis.sep_api.shared.integration.CorrelationIdFilter.MDC_KEY, correlationId);
            }
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                MDC.put(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY, idempotencyKey);
            }
            return new MDCBridge();
        }

        @Override
        public void close() {
            MDC.remove(com.dynamis.sep_api.shared.integration.CorrelationIdFilter.MDC_KEY);
            MDC.remove(IdempotencyKeyInterceptor.MDC_IDEMPOTENCY_KEY);
        }
    }
}
