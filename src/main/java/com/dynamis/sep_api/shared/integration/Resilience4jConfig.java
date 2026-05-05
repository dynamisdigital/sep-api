package com.dynamis.sep_api.shared.integration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuracao default de Resilience4j para chamadas externas.
 *
 * <p>Beans aqui criados sao defaults; cada provider pode declarar overrides em
 * {@code application.yml} (chave {@code resilience4j.circuitbreaker.instances.<nome>.*}).
 */
@Configuration
public class Resilience4jConfig {

    @Bean("defaultCircuitBreakerConfig")
    public CircuitBreakerConfig defaultCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
    }

    @Bean("defaultRetryConfig")
    public RetryConfig defaultRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(java.io.IOException.class)
                .build();
    }

    @Bean("defaultTimeLimiterConfig")
    public TimeLimiterConfig defaultTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30))
                .cancelRunningFuture(true)
                .build();
    }
}
