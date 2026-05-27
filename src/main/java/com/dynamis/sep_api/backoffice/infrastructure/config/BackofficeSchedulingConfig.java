package com.dynamis.sep_api.backoffice.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita {@code @Scheduled} para o modulo {@code backoffice} (Sprint 14 Task 14.2:
 * {@code VerificadorPendenciasJob}). Independente do {@code CobrancaSchedulingConfig} —
 * desligar cobranca em test nao deve desligar backoffice.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "app.backoffice.verificador.scheduling-habilitado",
        havingValue = "true",
        matchIfMissing = true)
public class BackofficeSchedulingConfig {}
