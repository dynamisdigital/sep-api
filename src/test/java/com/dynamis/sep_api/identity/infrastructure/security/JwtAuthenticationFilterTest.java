package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenProvider);

    @AfterEach
    void cleanContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void semHeaderAuthorizationSeguePelaChainSemAutenticar() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void tokenValidoPopulaSecurityContextComUsuarioAutenticado() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        UUID id = UUID.randomUUID();
        UsuarioAutenticado principal = new UsuarioAutenticado(id, "admin@sep.test", Role.ADMIN);
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");
        when(tokenProvider.tokenValido("token-valido")).thenReturn(true);
        when(tokenProvider.extrairPrincipal("token-valido")).thenReturn(principal);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        UserDetails autenticado = (UserDetails)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(autenticado).isInstanceOf(UsuarioAutenticado.class);
        assertThat(((UsuarioAutenticado) autenticado).id()).isEqualTo(id);
    }

    @Test
    void tokenInvalidoRetorna401ENaoChamaChain() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token-ruim");
        when(tokenProvider.tokenValido("token-ruim")).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token invalido");
        verifyNoInteractions(chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
