package com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin;

import com.dynamis.sep_api.onboarding.application.port.out.BackgroundCheckProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaPld;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinBackgroundCheckRequest;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.dto.CelcoinBackgroundCheckResponse;
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
 * Adapter HTTP real pro Celcoin Background Check (PLD). Selecionado quando {@code
 * app.pld.provider=celcoin}.
 *
 * <p>Endpoint consolidado: {@code POST /background-check} aceita {@code target_type=PESSOA} ou
 * {@code EMPRESA} e devolve resultados das 4 bases (COAF, OFAC, INTERPOL, MTE) em uma chamada.
 *
 * <p>Autenticacao: OAuth2 via {@link CelcoinOAuthTokenProvider} compartilhado. Resilience4j
 * instance {@code celcoin-background-check}.
 *
 * <p>LGPD: logs nao incluem payload de resposta nem body de erro. Detalhes de hit ficam em
 * {@code consulta_pld.payload_provider} apenas.
 */
@Component
@ConditionalOnProperty(name = "app.pld.provider", havingValue = "celcoin")
public class CelcoinBackgroundCheckProvider implements BackgroundCheckProvider {

    private static final Logger log = LoggerFactory.getLogger(CelcoinBackgroundCheckProvider.class);
    private static final String RESILIENCE_INSTANCE = "celcoin-background-check";

    private final RestClient restClient;
    private final CelcoinBackgroundCheckMapper mapper;
    private final CelcoinOAuthTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public CelcoinBackgroundCheckProvider(
            RestClientFactory factory,
            CelcoinBackgroundCheckMapper mapper,
            CelcoinBackgroundCheckProperties properties,
            CelcoinOAuthTokenProvider tokenProvider,
            ObjectMapper objectMapper) {
        this.restClient = factory.forProvider(RESILIENCE_INSTANCE, properties.baseUrl());
        this.mapper = mapper;
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaPld consultarPessoa(RequisicaoPld requisicao, String correlationId) {
        return executar(requisicao, correlationId);
    }

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    @Retry(name = RESILIENCE_INSTANCE)
    public RespostaPld consultarEmpresa(RequisicaoPld requisicao, String correlationId) {
        return executar(requisicao, correlationId);
    }

    private RespostaPld executar(RequisicaoPld requisicao, String correlationId) {
        CelcoinBackgroundCheckRequest payload = mapper.toCelcoinRequest(requisicao);
        try (MDCBridge ignored = MDCBridge.set(correlationId)) {
            CelcoinBackgroundCheckResponse response = restClient
                    .post()
                    .uri("/background-check")
                    .headers(this::headersAutenticacao)
                    .body(payload)
                    .retrieve()
                    .body(CelcoinBackgroundCheckResponse.class);
            String payloadCru = serializar(response);
            log.info(
                    "Celcoin PLD consultar solicitacaoId={} alvo={} resultados={}",
                    requisicao.solicitacaoId(),
                    requisicao.alvoTipo(),
                    response != null && response.resultados() != null
                            ? response.resultados().size()
                            : 0);
            return mapper.toRespostaPld(response, payloadCru);
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Celcoin PLD consultar falhou status={} correlationId={}",
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
            log.warn("Falha ao serializar payload Celcoin PLD pra persistencia");
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
