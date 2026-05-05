package com.dynamis.sep_api.shared.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Configura locale {@code pt-BR} e timezone {@code America/Sao_Paulo} no contexto JVM. Atende PRD
 * §RNF-01 (Configuracao Regional).
 *
 * <p>O Jackson e configurado via {@code application.yml} ({@code spring.jackson.time-zone}); este
 * bean garante que o restante da JVM (logs, formatadores legados, etc.) tambem opere no timezone
 * correto.
 */
@Configuration
public class LocaleConfig {

    @PostConstruct
    public void configure() {
        Locale ptBr = Locale.forLanguageTag("pt-BR");
        Locale.setDefault(ptBr);
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("America/Sao_Paulo")));
    }
}
