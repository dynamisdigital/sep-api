package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Enforcer server-side do flag {@code passwordResetRequired} (follow-up 5F-FIX-04 da Sprint 5).
 *
 * <p>Quando o principal autenticado e um {@link UsuarioAutenticado} com {@code
 * passwordResetRequired=true}, libera apenas as rotas estritamente necessarias para concluir a
 * redefinicao da senha:
 *
 * <ul>
 *   <li>{@code GET /api/v1/auth/me}: hidratar UI
 *   <li>{@code PATCH /api/v1/usuarios/{id}/senha}: trocar a senha
 *   <li>{@code POST /api/v1/auth/logout}: encerrar sessao limitada
 * </ul>
 *
 * Demais rotas autenticadas recebem {@code 403 Forbidden} com codigo {@code
 * AUTH-403-PASSWORD_RESET_REQUIRED}, impedindo que um cliente malicioso ignore o redirect do
 * frontend e use o token normalmente.
 */
@Component
public class PasswordResetEnforcementFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEnforcementFilter.class);

    /** Codigo do erro de dominio para clientes detectarem o estado. */
    public static final String ERROR_CODE = "AUTH-403-PASSWORD_RESET_REQUIRED";

    private static final Pattern ALTERAR_SENHA_PATH = Pattern.compile("^/api/v1/usuarios/[^/]+/senha$");
    private static final String AUTH_ME_PATH = "/api/v1/auth/me";
    private static final String AUTH_LOGOUT_PATH = "/api/v1/auth/logout";

    private final ObjectMapper objectMapper;

    public PasswordResetEnforcementFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof UsuarioAutenticado principal)
                || !principal.passwordResetRequired()) {
            chain.doFilter(request, response);
            return;
        }

        if (rotaPermitidaSobReset(request)) {
            chain.doFilter(request, response);
            return;
        }

        log.debug(
                "Bloqueando {} {} — usuario {} esta com passwordResetRequired=true",
                request.getMethod(),
                request.getRequestURI(),
                principal.id());
        responder403(request, response);
    }

    private boolean rotaPermitidaSobReset(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = UriComponentsBuilder.fromUriString(request.getRequestURI())
                .build()
                .getPath();
        if (uri == null) {
            return false;
        }
        if (HttpMethod.GET.matches(method) && AUTH_ME_PATH.equals(uri)) {
            return true;
        }
        if (HttpMethod.POST.matches(method) && AUTH_LOGOUT_PATH.equals(uri)) {
            return true;
        }
        return HttpMethod.PATCH.matches(method)
                && ALTERAR_SENHA_PATH.matcher(uri).matches();
    }

    private void responder403(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ErrorResponseDto body = ErrorResponseDto.of(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                ERROR_CODE + ": redefinicao de senha obrigatoria antes de continuar.",
                request.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY));
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
