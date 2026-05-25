package com.dynamis.sep_api.cobranca.infrastructure.config;

import com.dynamis.sep_api.cobranca.infrastructure.adapter.notification.NotificacaoProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Habilita {@link NotificacaoProperties} (Sprint 13 - ADR 0014). */
@Configuration
@EnableConfigurationProperties(NotificacaoProperties.class)
public class CobrancaNotificacaoConfig {}
