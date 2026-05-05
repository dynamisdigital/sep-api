package com.dynamis.sep_api.identity.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuracao base de seguranca para a Sprint 1.
 *
 * <p>Nesta sprint apenas CORS e a politica de sessao stateless sao definidas. O filtro JWT,
 * autorizacao por perfil/ownership e demais regras entram na Sprint 3.
 *
 * <p>Endpoints liberados publicamente nesta fase:
 *
 * <ul>
 *   <li>{@code /actuator/**}: healthcheck, info, metrics, prometheus (uteis em dev/CI)
 *   <li>{@code /v3/api-docs} + {@code /v3/api-docs/**}: contratos OpenAPI brutos
 *   <li>{@code /swagger-ui.html} + {@code /swagger-ui/**} + {@code /webjars/**}: UI do Swagger e
 *       seus assets estaticos (CSS/JS)
 * </ul>
 *
 * <p>A Sprint 3 vai trocar este permit-all amplo de {@code /actuator/**} por configuracao por
 * profile (publico em dev, autenticado em prod).
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
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/actuator/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/webjars/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/usuarios")
                        .permitAll()
                        // qualquer outra rota fica restrita ate Sprint 3 plugar JWT
                        .anyRequest()
                        .authenticated());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
