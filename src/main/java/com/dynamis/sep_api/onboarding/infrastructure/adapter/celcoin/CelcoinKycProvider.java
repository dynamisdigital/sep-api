package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.KycProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoVerificacaoKyc;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaInicioVerificacao;
import com.dynamis.sep_api.onboarding.application.port.out.dto.ResultadoKycProvider;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKycRequest;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKycResponse;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKycResultadoResponse;
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
 * Adapter HTTP real para o Celcoin Onboarding KYC PF. Selecionado quando {@code
 * app.kyc.provider=celcoin}.
 *
 * <p>Autenticacao: OAuth2 client-credentials via {@link CelcoinOAuthTokenProvider} (cache de
 * token + refresh automatico). Header {@code Authorization: Bearer <token>}.
 *
 * <p>Resilience4j (instance {@code celcoin-kyc}): retry em falhas transitorias (5xx +
 * IOException/ResourceAccessException) com circuit breaker. Timeout via {@link RestClientFactory}
 * ({@code app.integration.read-timeout-seconds=30}).
 *
 * <p>LGPD: logs nunca incluem payload de resposta nem body de erro do provider — apenas status
 * HTTP, correlationId e identificadores tecnicos sao logados.
 */
@Component
@ConditionalOnProperty(name = "app.kyc.provider", havingValue = "celcoin")
public class CelcoinKycProvider implements KycProvider {

    private static final Logger log = LoggerFactory.getLogger(CelcoinKycProvider.class);
    private static final String RESILIENCE_INSTANCE = "celcoin-kyc";

    private final RestClient restClient;
    private final CelcoinKycMapper mapper;
    private final CelcoinOAuthTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public CelcoinKycProvider(
            RestClientFactory factory,
            CelcoinKycMapper mapper,
            CelcoinKycProperties properties,
            CelcoinOAuthTokenProvider tokenProvider,
            ObjectMapper objectMapper) {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new IllegalStateException("app.celcoin.kyc.base-url obrigatorio quando app.kyc.provider=celcoin");
        }
        this.restClient = factory.forProvider(RESILIENCE_INSTANCE, properties.baseUrl());
        this.mapper = mapper;
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaInicioVerificacao iniciarVerificacao(RequisicaoVerificacaoKyc requisicao, String correlationId) {
        CelcoinKycRequest payload = mapper.toCelcoinRequest(requisicao);
        // Idempotency-Key e responsabilidade do caller (Task 6.3 grava chave deterministica
        // 'solicitacaoId:revisaoDocumentos' no MDC antes desta chamada). Adapter NAO sobrescreve.
        try (MDCBridge ignored = MDCBridge.set(correlationId)) {
            CelcoinKycResponse response = restClient
                    .post()
                    .uri("/verifications")
                    .headers(this::headersAutenticacao)
                    .body(payload)
                    .retrieve()
                    .body(CelcoinKycResponse.class);
            CelcoinKycResponse validada = exigirInicio(response);
            log.info(
                    "Celcoin KYC iniciada solicitacaoId={} verificationId={}",
                    requisicao.solicitacaoId(),
                    validada.idVerificacao());
            return mapper.toRespostaInicio(validada);
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Celcoin KYC iniciar falhou status={} correlationId={}",
                    ex.getStatusCode().value(),
                    correlationId);
            throw ex;
        }
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public ResultadoKycProvider consultarResultado(String idVerificacaoExterna, String correlationId) {
        try (MDCBridge ignored = MDCBridge.set(correlationId)) {
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
                    "Celcoin KYC consultar falhou status={} correlationId={}",
                    ex.getStatusCode().value(),
                    correlationId);
            throw ex;
        }
    }

    /** Resposta invalida nao e falha transiente (Sprint 32 Task 32.3): falha clara, sem retry. */
    private CelcoinKycResponse exigirInicio(CelcoinKycResponse response) {
        if (response == null
                || response.idVerificacao() == null
                || response.idVerificacao().isBlank()) {
            throw new IllegalStateException("Resposta invalida do Celcoin KYC (esperado verification_id)");
        }
        return response;
    }

    private void headersAutenticacao(HttpHeaders headers) {
        headers.setBearerAuth(tokenProvider.accessToken(CelcoinOAuthTokenProvider.ProviderKey.KYC));
    }

    private String serializar(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Falha ao serializar payload Celcoin para persistencia");
            return "{}";
        }
    }

    /**
     * AutoCloseable que popula MDC apenas com {@code correlationId} e o restaura ao fim. NAO
     * gerencia {@code idempotencyKey} — esse e responsabilidade do caller (use case), conforme
     * Task 6.3.
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
