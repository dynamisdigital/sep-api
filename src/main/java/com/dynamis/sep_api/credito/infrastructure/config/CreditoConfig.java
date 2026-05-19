package com.dynamis.sep_api.credito.infrastructure.config;

import com.dynamis.sep_api.credito.application.service.CreditoMotorProperties;
import com.dynamis.sep_api.credito.infrastructure.adapter.celcoin.CelcoinOpenFinanceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao do modulo {@code credito}. Ativa binding de {@link CreditoMotorProperties}
 * ({@code app.credito.motor.*}) e {@link CelcoinOpenFinanceProperties}
 * ({@code app.celcoin.open-finance.*}) — Sprint 9.
 */
@Configuration
@EnableConfigurationProperties({CreditoMotorProperties.class, CelcoinOpenFinanceProperties.class})
public class CreditoConfig {}
