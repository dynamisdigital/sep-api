package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Entry point que produz {@link ErrorResponseDto} JSON quando uma requisicao chega a uma rota
 * protegida sem autenticacao valida. Sem este componente, o Spring Security 6 default retorna
 * {@code 403} em HTML; aqui forcamos {@code 401} com payload padrao.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ErrorResponseDto body = ErrorResponseDto.of(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Autenticacao requerida",
                request.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
