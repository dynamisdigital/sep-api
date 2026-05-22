package com.dynamis.sep_api.cobranca.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita {@code @Scheduled} para o modulo {@code cobranca} (Sprint 12 Task 12.5: job diario
 * {@code MarcarParcelaAtrasadaJob}). Restrito ao modulo via {@code @ConditionalOnProperty} para
 * permitir desligar em ITs alheios via {@code application-test.yml}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.cobranca.scheduling-habilitado", havingValue = "true", matchIfMissing = true)
public class CobrancaSchedulingConfig {}
