package com.dynamis.sep_api.pix.infrastructure.config;

import com.dynamis.sep_api.pix.infrastructure.adapter.celcoin.CelcoinPixProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao do modulo {@code pix} (Epic 15 / Sprint 19). Ativa binding de
 * {@link CelcoinPixProperties} ({@code app.celcoin.pix.*}).
 */
@Configuration
@EnableConfigurationProperties({CelcoinPixProperties.class})
public class PixConfig {}
