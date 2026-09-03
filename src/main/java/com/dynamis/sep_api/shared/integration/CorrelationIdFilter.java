package com.dynamis.sep_api.shared.integration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Servlet filter que injeta um {@code correlationId} (UUID) no MDC para cada request. Se o header
 * {@code X-Correlation-Id} estiver presente, ele e propagado; caso contrario, um novo UUID e
 * gerado.
 *
 * <p>O ID e exposto na resposta no mesmo header e e usado em logs estruturados (Logback pattern do
 * {@code logback-spring.xml}) e propagado para chamadas a providers externos (ver {@link
 * RestClientFactory}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    private static final int MAX_LENGTH = 128;
    private static final Pattern VALID_VALUE = Pattern.compile("[A-Za-z0-9._:-]{1," + MAX_LENGTH + "}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (!isValid(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    static boolean isValid(String value) {
        return value != null && VALID_VALUE.matcher(value).matches();
    }
}
