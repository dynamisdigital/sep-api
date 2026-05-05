package com.dynamis.sep_api.shared.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Factory de {@link RestClient} para chamadas a providers externos.
 *
 * <p>Cada provider e configurado por nome (ex.: {@code forProvider("celcoin")}); timeouts e base
 * URL sao lidos de propriedades em {@code app.integration.<provider>.*}. O
 * {@link IdempotencyKeyInterceptor} e adicionado por padrao.
 *
 * <p>O Resilience4j (circuit breaker, retry, timeout) e aplicado de forma declarativa nos beans
 * dos providers via {@code @CircuitBreaker}, {@code @Retry} e {@code @TimeLimiter} configurados
 * em {@link Resilience4jConfig}.
 */
@Component
public class RestClientFactory {

    @Value("${app.integration.connect-timeout-seconds:10}")
    private long connectTimeoutSeconds;

    @Value("${app.integration.read-timeout-seconds:30}")
    private long readTimeoutSeconds;

    public RestClient forProvider(String providerName, String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(new IdempotencyKeyInterceptor())
                .defaultHeader("User-Agent", "sep-api/" + providerName)
                .build();
    }
}
