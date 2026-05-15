package com.dynamis.sep_api.onboarding.infrastructure.config;

import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.CelcoinBackgroundCheckProperties;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.CelcoinKybProperties;
import com.dynamis.sep_api.onboarding.infrastructure.adapter.celcoin.CelcoinKycProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao do modulo {@code onboarding}.
 *
 * <p>Ativa binding de {@link CelcoinKycProperties} ({@code app.celcoin.kyc.*}),
 * {@link CelcoinKybProperties} ({@code app.celcoin.kyb.*}) e
 * {@link CelcoinBackgroundCheckProperties} ({@code app.celcoin.background-check.*})
 * independentemente dos adapters selecionados.
 */
@Configuration
@EnableConfigurationProperties({
    CelcoinKycProperties.class,
    CelcoinKybProperties.class,
    CelcoinBackgroundCheckProperties.class
})
public class OnboardingConfig {}
