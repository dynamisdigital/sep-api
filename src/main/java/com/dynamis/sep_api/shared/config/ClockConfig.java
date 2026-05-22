package com.dynamis.sep_api.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Bean compartilhado de {@link Clock} fixado em {@code America/Sao_Paulo}. Use cases que dependem
 * do "agora" (Sprint 12 Task 12.5: calculo de valor atualizado e job diario de atraso) recebem
 * este bean via construtor, mantendo testes deterministicos quando substituido por
 * {@code Clock.fixed(...)}.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
