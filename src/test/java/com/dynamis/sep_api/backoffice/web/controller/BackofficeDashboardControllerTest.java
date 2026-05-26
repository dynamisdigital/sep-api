package com.dynamis.sep_api.backoffice.web.controller;

import com.dynamis.sep_api.backoffice.application.dto.DashboardBackoffice;
import com.dynamis.sep_api.backoffice.application.dto.InadimplenciaConsolidada;
import com.dynamis.sep_api.backoffice.application.usecase.ConsultarVisaoConsolidadaUseCase;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.StepUpEnforcementAspect;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BackofficeDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    ApiExceptionHandler.class,
    BackofficeDashboardControllerTest.MethodSecurityTestConfig.class,
    StepUpEnforcementAspect.class
})
class BackofficeDashboardControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    @org.springframework.context.annotation.EnableAspectJAutoProxy
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ConsultarVisaoConsolidadaUseCase consultarVisao;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private StepUpTokenService stepUpTokenService;
    @MockBean private UsuarioRepository usuarioRepository;

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(UUID id, Role role) {
        UsuarioAutenticado p = new UsuarioAutenticado(id, "op@sep.test", role);
        var auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        Usuario u = Usuario.criar("op@sep.test", "hash", role);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
    }

    @Test
    void consultar_backoffice200ComNoCache() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE);
        when(consultarVisao.consultar()).thenReturn(dashboardVazio());

        mockMvc.perform(get("/api/v1/backoffice/dashboard"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.geradoEm").exists());
    }

    @Test
    void consultar_financeiro200() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        when(consultarVisao.consultar()).thenReturn(dashboardVazio());

        mockMvc.perform(get("/api/v1/backoffice/dashboard")).andExpect(status().isOk());
    }

    @Test
    void consultar_admin200() throws Exception {
        autenticar(UUID.randomUUID(), Role.ADMIN);
        when(consultarVisao.consultar()).thenReturn(dashboardVazio());

        mockMvc.perform(get("/api/v1/backoffice/dashboard")).andExpect(status().isOk());
    }

    @Test
    void consultar_cliente_403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);

        mockMvc.perform(get("/api/v1/backoffice/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    void consultar_semAutenticacao_401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/backoffice/dashboard")).andExpect(status().isUnauthorized());
    }

    private DashboardBackoffice dashboardVazio() {
        return new DashboardBackoffice(
                List.of(),
                List.of(),
                List.of(),
                Duration.ZERO,
                0L,
                List.of(),
                BigDecimal.ZERO,
                InadimplenciaConsolidada.vazia(),
                List.of(),
                Instant.parse("2026-05-26T12:00:00Z"));
    }
}
