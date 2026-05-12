package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PasswordResetEnforcementFilterTest {

    private final PasswordResetEnforcementFilter filter =
            new PasswordResetEnforcementFilter(new ObjectMapper().registerModule(new JavaTimeModule()));

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void semAutenticacaoPassaAdiante() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/usuarios");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void usuarioSemResetPassaAdiante() throws Exception {
        autenticar(new UsuarioAutenticado(UUID.randomUUID(), "u@sep.test", Role.CLIENTE, false));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/usuarios");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    void usuarioComResetEmAuthMePassaAdiante() throws Exception {
        autenticar(new UsuarioAutenticado(UUID.randomUUID(), "u@sep.test", Role.CLIENTE, true));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    void usuarioComResetEmPatchSenhaPassaAdiante() throws Exception {
        autenticar(new UsuarioAutenticado(UUID.randomUUID(), "u@sep.test", Role.CLIENTE, true));
        MockHttpServletRequest req =
                new MockHttpServletRequest("PATCH", "/api/v1/usuarios/1f0799c0-98b9-6d9d-bc4a-7d6f5b771001/senha");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    void usuarioComResetEmLogoutPassaAdiante() throws Exception {
        autenticar(new UsuarioAutenticado(UUID.randomUUID(), "u@sep.test", Role.CLIENTE, true));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    void usuarioComResetEmRotaQualquerRetorna403JsonComCodigo() throws Exception {
        autenticar(new UsuarioAutenticado(UUID.randomUUID(), "u@sep.test", Role.CLIENTE, true));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/usuarios");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentType()).contains("application/json");
        assertThat(res.getContentAsString()).contains(PasswordResetEnforcementFilter.ERROR_CODE);
        assertThat(res.getContentAsString()).contains("\"status\":403");
    }

    @Test
    void usuarioComResetEmGetSenhaRetorna403() throws Exception {
        // metodo errado em rota sensivel ainda eh bloqueado
        autenticar(new UsuarioAutenticado(UUID.randomUUID(), "u@sep.test", Role.CLIENTE, true));
        MockHttpServletRequest req =
                new MockHttpServletRequest("GET", "/api/v1/usuarios/1f0799c0-98b9-6d9d-bc4a-7d6f5b771001/senha");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(403);
    }

    private void autenticar(UsuarioAutenticado principal) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
