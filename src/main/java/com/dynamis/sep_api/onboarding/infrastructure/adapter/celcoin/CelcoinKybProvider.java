package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.KybProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoKyb;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaKyb;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKybRequest;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinKybResponse;
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
 * Adapter HTTP real pro Celcoin Onboarding KYB PJ. Selecionado quando {@code
 * app.kyb.provider=celcoin}.
 *
 * <p>Autenticacao: OAuth2 client-credentials via {@link CelcoinOAuthTokenProvider} (compartilhado
 * com KYC). Header {@code Authorization: Bearer <token>}.
 *
 * <p>Resilience4j (instance {@code celcoin-kyb}): retry em falhas transitorias (5xx +
 * IOException/ResourceAccessException) com circuit breaker.
 *
 * <p>LGPD: logs nunca incluem payload de resposta nem body de erro do provider — apenas status
 * HTTP, correlationId e identificadores tecnicos sao logados.
 */
@Component
@ConditionalOnProperty(name = "app.kyb.provider", havingValue = "celcoin")
public class CelcoinKybProvider implements KybProvider {

    private static final Logger log = LoggerFactory.getLogger(CelcoinKybProvider.class);
    private static final String RESILIENCE_INSTANCE = "celcoin-kyb";

    private final RestClient restClient;
    private final CelcoinKybMapper mapper;
    private final CelcoinOAuthTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public CelcoinKybProvider(
            RestClientFactory factory,
            CelcoinKybMapper mapper,
            CelcoinKybProperties properties,
            CelcoinOAuthTokenProvider tokenProvider,
            ObjectMapper objectMapper) {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new IllegalStateException("app.celcoin.kyb.base-url obrigatorio quando app.kyb.provider=celcoin");
        }
        this.restClient = factory.forProvider(RESILIENCE_INSTANCE, properties.baseUrl());
        this.mapper = mapper;
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaKyb consultarCnpj(RequisicaoKyb requisicao, String correlationId) {
        CelcoinKybRequest payload = mapper.toCelcoinRequest(requisicao);
        try (MDCBridge ignored = MDCBridge.set(correlationId)) {
            CelcoinKybResponse response = restClient
                    .post()
                    .uri("/companies")
                    .headers(this::headersAutenticacao)
                    .body(payload)
                    .retrieve()
                    .body(CelcoinKybResponse.class);
            // Resposta invalida nao e falha transiente (Sprint 32 Task 32.3): falha clara, sem
            // retry — corpo vazio nao pode virar situacao cadastral "desconhecida" silenciosa.
            if (response == null) {
                throw new IllegalStateException("Resposta invalida do Celcoin KYB (corpo vazio)");
            }
            String payloadCru = serializar(response);
            log.info(
                    "Celcoin KYB consultarCnpj solicitacaoId={} situacao={} representantes={}",
                    requisicao.solicitacaoId(),
                    response.situacao(),
                    response.representantes() != null
                            ? response.representantes().size()
                            : 0);
            return mapper.toRespostaKyb(response, payloadCru);
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Celcoin KYB consultarCnpj falhou status={} correlationId={}",
                    ex.getStatusCode().value(),
                    correlationId);
            throw ex;
        }
    }

    private void headersAutenticacao(HttpHeaders headers) {
        headers.setBearerAuth(tokenProvider.accessToken(CelcoinOAuthTokenProvider.ProviderKey.KYB));
    }

    private String serializar(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Falha ao serializar payload Celcoin KYB pra persistencia");
            return "{}";
        }
    }

    /** AutoCloseable que popula MDC apenas com {@code correlationId} e o restaura ao fim. */
    private static final class MDCBridge implements AutoCloseable {

        private final String correlationAnterior;

        private MDCBridge(String correlationAnterior) {
            this.correlationAnterior = correlationAnterior;
        }

        static MDCBridge set(String correlationId) {
            String anterior = MDC.get(com.dynamis.sep_api.shared.integration.CorrelationIdFilter.MDC_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put(com.dynamis.sep_api.shared.integration.CorrelationIdFilter.MDC_KEY, correlationId);
            }
            return new MDCBridge(anterior);
        }

        @Override
        public void close() {
            if (correlationAnterior == null) {
                MDC.remove(com.dynamis.sep_api.shared.integration.CorrelationIdFilter.MDC_KEY);
            } else {
                MDC.put(com.dynamis.sep_api.shared.integration.CorrelationIdFilter.MDC_KEY, correlationAnterior);
            }
        }
    }
}
