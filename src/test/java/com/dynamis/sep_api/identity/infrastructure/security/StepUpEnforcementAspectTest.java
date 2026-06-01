package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StepUpEnforcementAspectTest {

    private StepUpTokenService tokenService;
    private UsuarioRepository usuarioRepository;
    private StepUpEnforcementAspect aspect;

    private final UUID operadorId = UUID.randomUUID();
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        tokenService = mock(StepUpTokenService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        aspect = new StepUpEnforcementAspect(tokenService, usuarioRepository);

        UsuarioAutenticado principal = new UsuarioAutenticado(operadorId, "operador@empresa.com", Role.FINANCEIRO);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));

        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private Usuario usuarioComMfa(boolean habilitado) {
        Usuario usuario = mock(Usuario.class);
        when(usuario.isMfaHabilitado()).thenReturn(habilitado);
        return usuario;
    }

    @Test
    void estrito_mfaDesabilitadoComToken_negaSemSequerValidarToken() {
        Usuario usuario = usuarioComMfa(false);
        when(usuarioRepository.findById(operadorId)).thenReturn(Optional.of(usuario));
        request.addHeader(StepUpEnforcementAspect.HEADER, "token-qualquer");

        assertThatThrownBy(() -> aspect.aplicarEstrito())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("MFA");

        verify(tokenService, never()).validarEConsumir(any());
    }

    @Test
    void estrito_usuarioAusente_nega() {
        when(usuarioRepository.findById(operadorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aspect.aplicarEstrito()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void estrito_mfaHabilitadoTokenValido_permite() {
        Usuario usuario = usuarioComMfa(true);
        when(usuarioRepository.findById(operadorId)).thenReturn(Optional.of(usuario));
        request.addHeader(StepUpEnforcementAspect.HEADER, "token-valido");
        when(tokenService.validarEConsumir("token-valido")).thenReturn(Optional.of(operadorId));

        assertThatCode(() -> aspect.aplicarEstrito()).doesNotThrowAnyException();
    }

    @Test
    void estrito_mfaHabilitadoTokenInvalido_nega() {
        Usuario usuario = usuarioComMfa(true);
        when(usuarioRepository.findById(operadorId)).thenReturn(Optional.of(usuario));
        when(tokenService.validarEConsumir(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aspect.aplicarEstrito()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void legado_mfaDesabilitado_aindaFazBypass() {
        Usuario usuario = usuarioComMfa(false);
        when(usuarioRepository.findById(operadorId)).thenReturn(Optional.of(usuario));

        // Regressao: o ramo legado @RequireStepUp mantem o bypass de migracao pre-MFA.
        assertThatCode(() -> aspect.aplicar()).doesNotThrowAnyException();
        verify(tokenService, never()).validarEConsumir(any());
    }
}
