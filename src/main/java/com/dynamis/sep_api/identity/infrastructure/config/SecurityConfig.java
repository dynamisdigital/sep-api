package com.dynamis.sep_api.identity.infrastructure.config;

import com.dynamis.sep_api.identity.infrastructure.security.ApiAccessDeniedHandler;
import com.dynamis.sep_api.identity.infrastructure.security.ApiAuthenticationEntryPoint;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuracao de seguranca consolidada apos a Sprint 3 e estendida na Sprint 4.
 *
 * <p>Sprint 3 introduz autenticacao JWT: registra o {@link JwtAuthenticationFilter} antes do
 * {@code UsernamePasswordAuthenticationFilter} e habilita method security para
 * {@code @PreAuthorize}. Sprint 4 conecta {@link ApiAuthenticationEntryPoint} +
 * {@link ApiAccessDeniedHandler} para 401/403 padronizados em JSON e libera o endpoint de
 * webhooks (autenticacao por HMAC, nao por JWT).
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final UrlBasedCorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;

    public SecurityConfig(
            UrlBasedCorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
            ApiAccessDeniedHandler apiAccessDeniedHandler) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
        this.apiAccessDeniedHandler = apiAccessDeniedHandler;
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
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
