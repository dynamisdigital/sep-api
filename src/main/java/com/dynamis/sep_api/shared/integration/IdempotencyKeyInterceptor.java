package com.dynamis.sep_api.shared.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Interceptor que adiciona o header {@code Idempotency-Key} a chamadas HTTP outbound a providers
 * externos. A chave e lida do MDC (chave {@value #MDC_IDEMPOTENCY_KEY}); o caller e responsavel por
 * colocar o valor antes da chamada.
 *
 * <p>Tambem propaga {@link CorrelationIdFilter#HEADER} para o provider, util para correlacionar
 * logs ponta a ponta com a Celcoin.
 */
public class IdempotencyKeyInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyInterceptor.class);

    public static final String MDC_IDEMPOTENCY_KEY = "idempotencyKey";
    public static final String HEADER_IDEMPOTENCY = "Idempotency-Key";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String idempotencyKey = MDC.get(MDC_IDEMPOTENCY_KEY);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            request.getHeaders().add(HEADER_IDEMPOTENCY, idempotencyKey);
        }
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            request.getHeaders().add(CorrelationIdFilter.HEADER, correlationId);
        }
        log.atDebug()
                .addKeyValue("event", "outbound_http_request")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getURI().getPath())
                .log("Outbound HTTP request");
        return execution.execute(request, body);
    }
}
