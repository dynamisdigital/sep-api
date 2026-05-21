package com.dynamis.sep_api.contratos.infrastructure.config;

import com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura.ClicksignAssinaturaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuracao Spring do modulo {@code contratos} — ConfigurationProperties (Sprint 11). */
@Configuration
@EnableConfigurationProperties({ClicksignAssinaturaProperties.class})
public class ContratosConfig {}
