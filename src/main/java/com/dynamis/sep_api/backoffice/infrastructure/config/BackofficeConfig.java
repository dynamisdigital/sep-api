package com.dynamis.sep_api.backoffice.infrastructure.config;

import com.dynamis.sep_api.backoffice.application.job.BackofficeVerificadorProperties;
import com.dynamis.sep_api.backoffice.application.usecase.BackofficeDashboardProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita properties do backoffice: {@link BackofficeVerificadorProperties} (Sprint 14 Task 14.2)
 * e {@link BackofficeDashboardProperties} (Sprint 14 Task 14.5).
 */
@Configuration
@EnableConfigurationProperties({BackofficeVerificadorProperties.class, BackofficeDashboardProperties.class})
public class BackofficeConfig {}
