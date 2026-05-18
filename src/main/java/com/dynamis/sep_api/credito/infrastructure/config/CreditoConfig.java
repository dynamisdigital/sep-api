package com.dynamis.sep_api.credito.infrastructure.config;

import com.dynamis.sep_api.credito.application.service.CreditoMotorProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao do modulo {@code credito}. Ativa binding de {@link CreditoMotorProperties}
 * ({@code app.credito.motor.*}).
 */
@Configuration
@EnableConfigurationProperties(CreditoMotorProperties.class)
public class CreditoConfig {}
