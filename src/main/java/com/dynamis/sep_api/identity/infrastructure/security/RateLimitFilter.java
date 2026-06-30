package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Aplica rate limit por IP em rotas sensiveis de autenticacao (Sprint 5 Task 5.4): {@code POST
 * /api/v1/auth/login} e {@code POST /api/v1/auth/totp/verify}.
 *
 * <p>Usa o {@link RateLimiterRegistry} default do Resilience4j com configs especificas por
 * endpoint (chave dinamica = {@code "login:" + ip} ou {@code "totp-verify:" + ip}). Ao exceder,
 * devolve {@code 429 Too Many Requests} com {@link ErrorResponseDto} JSON e nao chama a chain.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    static final String LOGIN_PATH = "/api/v1/auth/login";
    static final String TOTP_VERIFY_PATH = "/api/v1/auth/totp/verify";

    private final RateLimiterRegistry registry;
    private final ObjectMapper objectMapper;
    private final RateLimiterConfig loginConfig;
    private final RateLimiterConfig totpVerifyConfig;

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.registry = RateLimiterRegistry.ofDefaults();
        this.objectMapper = objectMapper;
        this.loginConfig = RateLimiterConfig.custom()
                .limitForPeriod(properties.getLoginPerMinutePerIp())
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        this.totpVerifyConfig = RateLimiterConfig.custom()
                .limitForPeriod(properties.getTotpVerifyPerMinutePerIp())
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        RateLimiterConfig config = null;
        String prefixo = null;
        if (LOGIN_PATH.equals(path)) {
            config = loginConfig;
            prefixo = "login";
        } else if (TOTP_VERIFY_PATH.equals(path)) {
            config = totpVerifyConfig;
            prefixo = "totp-verify";
        }
        if (config == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String ip = extrairIp(request);
        String key = prefixo + ":" + ip;
        boolean permitido = registry.rateLimiter(key, config).acquirePermission();
        if (!permitido) {
            log.atWarn()
                    .addKeyValue("event", "rate_limit_exceeded")
                    .addKeyValue("path", path)
                    .log("Rate limit excedido");
            escreverErro429(response, path);
            return;
        }
        filterChain.doFilter(request, response);
    }

    public static String extrairIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remoto = request.getRemoteAddr();
        return remoto != null ? remoto : "unknown";
    }

    private void escreverErro429(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponseDto erro = ErrorResponseDto.of(
                429,
                "Too Many Requests",
                "Limite de requisicoes excedido. Aguarde antes de tentar novamente.",
                path,
                MDC.get("correlationId"));
        objectMapper.writeValue(response.getOutputStream(), erro);
    }
}
