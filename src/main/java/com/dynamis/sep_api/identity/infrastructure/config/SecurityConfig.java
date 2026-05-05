package com.dynamis.sep_api.identity.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuracao base de seguranca para a Sprint 1.
 *
 * <p>Nesta sprint apenas CORS e a politica de sessao stateless sao definidas. O filtro JWT,
 * autorizacao por perfil/ownership e demais regras entram na Sprint 3.
 *
 * @see com.dynamis.sep_api.shared.config.CorsConfig
 */
@Configuration
public class SecurityConfig {

    private final UrlBasedCorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(UrlBasedCorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // endpoints publicos basicos da Sprint 1 (Sprint 3 trara cadastro de usuario e login)
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                        .permitAll()
                        // qualquer outra rota fica restrita ate Sprint 3 plugar JWT
                        .anyRequest()
                        .authenticated())
                // basic auth temporario apenas para nao bloquear /actuator durante dev;
                // sera removido na Sprint 3 quando JWT entrar
                .httpBasic(httpBasic -> {});

        return http.build();
    }
}
