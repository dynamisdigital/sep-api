package com.dynamis.sep_api.onboarding.infrastructure.config;

import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.CelcoinKybProperties;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.CelcoinKycProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao do modulo {@code onboarding}.
 *
 * <p>Ativa binding de {@link CelcoinKycProperties} ({@code app.celcoin.kyc.*}) e
 * {@link CelcoinKybProperties} ({@code app.celcoin.kyb.*}) independentemente dos adapters
 * selecionados, porque webhook validators (KYC Task 6.4 + KYB Task 7.6) tambem leem secrets HMAC.
 */
@Configuration
@EnableConfigurationProperties({CelcoinKycProperties.class, CelcoinKybProperties.class})
public class OnboardingConfig {}
