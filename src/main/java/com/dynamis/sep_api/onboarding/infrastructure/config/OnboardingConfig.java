package com.dynamis.sep_api.onboarding.infrastructure.config;

import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.CelcoinKycProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao do modulo {@code onboarding}.
 *
 * <p>Ativa binding de {@link CelcoinKycProperties} ({@code app.celcoin.kyc.*}) independentemente do
 * adapter selecionado, porque o webhook validator (Task 6.4) tambem precisa ler o secret HMAC.
 */
@Configuration
@EnableConfigurationProperties(CelcoinKycProperties.class)
public class OnboardingConfig {}
