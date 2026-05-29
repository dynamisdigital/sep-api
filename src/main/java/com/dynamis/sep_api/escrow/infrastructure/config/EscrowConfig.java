package com.dynamis.sep_api.escrow.infrastructure.config;

import com.dynamis.sep_api.escrow.infrastructure.adapter.celcoin.CelcoinEscrowProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao do modulo {@code escrow} (Epic 15 / Sprint 19). Ativa binding de
 * {@link CelcoinEscrowProperties} ({@code app.celcoin.escrow.*}).
 */
@Configuration
@EnableConfigurationProperties({CelcoinEscrowProperties.class})
public class EscrowConfig {}
