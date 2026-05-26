package com.dynamis.sep_api.backoffice.infrastructure.config;

import com.dynamis.sep_api.backoffice.application.job.BackofficeVerificadorProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Habilita {@link BackofficeVerificadorProperties} (Sprint 14 Task 14.2). */
@Configuration
@EnableConfigurationProperties(BackofficeVerificadorProperties.class)
public class BackofficeConfig {}
