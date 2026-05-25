package com.dynamis.sep_api.cobranca.infrastructure.config;

import com.dynamis.sep_api.cobranca.application.service.workflow.WorkflowCobrancaProperties;
import com.dynamis.sep_api.cobranca.infrastructure.adapter.notification.NotificacaoProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita {@link NotificacaoProperties} (Sprint 13 Task 13.1 - ADR 0014) e {@link
 * WorkflowCobrancaProperties} (Sprint 13 Task 13.4).
 */
@Configuration
@EnableConfigurationProperties({NotificacaoProperties.class, WorkflowCobrancaProperties.class})
public class CobrancaNotificacaoConfig {}
