package com.dynamis.sep_api.usuarios.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.usuarios.application.usecase.ConsultarUsuarioUseCase;
import com.dynamis.sep_api.usuarios.application.usecase.CriarUsuarioUseCase;
import com.dynamis.sep_api.usuarios.application.usecase.ListarUsuariosUseCase;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, UsuarioControllerSecurityTest.MethodSecurityTestConfig.class})
class UsuarioControllerSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CriarUsuarioUseCase criarUsuarioUseCase;

    @MockBean
    private ConsultarUsuarioUseCase consultarUsuarioUseCase;

    @MockBean
    private ListarUsuariosUseCase listarUsuariosUseCase;

    @MockBean
    private com.dynamis.sep_api.usuarios.application.usecase.AlterarSenhaUseCase alterarSenhaUseCase;

    @MockBean
    private UsuarioMapper mapper;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(UsuarioAutenticado principal) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private UsuarioResponseDto fakeResponse(UUID id, Role role) {
        return new UsuarioResponseDto(
                id,
                id + "@sep.test",
                role,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "system",
                "system",
                false,
                false);
    }

    @Test
    void adminConsultaUsuarioAlheioPorId() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID alvoId = UUID.randomUUID();
        autenticar(new UsuarioAutenticado(adminId, "admin@sep.test", Role.ADMIN));
        Usuario alvo = Usuario.criar("alvo@sep.test", "$2a$h", Role.CLIENTE);
        when(consultarUsuarioUseCase.executar(any(), any())).thenReturn(alvo);
        when(mapper.toResponse(alvo)).thenReturn(fakeResponse(alvoId, Role.CLIENTE));

        mockMvc.perform(get("/api/v1/usuarios/" + alvoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLIENTE"));
    }

    @Test
    void clienteConsultaProprioUsuario() throws Exception {
        UUID id = UUID.randomUUID();
        autenticar(new UsuarioAutenticado(id, "cliente@sep.test", Role.CLIENTE));
        Usuario proprio = Usuario.criar("cliente@sep.test", "$2a$h", Role.CLIENTE);
        when(consultarUsuarioUseCase.executar(any(), any())).thenReturn(proprio);
        when(mapper.toResponse(proprio)).thenReturn(fakeResponse(id, Role.CLIENTE));

        mockMvc.perform(get("/api/v1/usuarios/" + id)).andExpect(status().isOk());
    }

    @Test
    void clienteConsultandoIdAlheioRetorna403() throws Exception {
        UUID id = UUID.randomUUID();
        autenticar(new UsuarioAutenticado(id, "cliente@sep.test", Role.CLIENTE));
        UUID alheio = UUID.randomUUID();
        when(consultarUsuarioUseCase.executar(any(), any())).thenThrow(new AccessDeniedException("acesso negado"));

        mockMvc.perform(get("/api/v1/usuarios/" + alheio)).andExpect(status().isForbidden());
    }

    @Test
    void adminListaTodos() throws Exception {
        autenticar(new UsuarioAutenticado(UUID.randomUUID(), "admin@sep.test", Role.ADMIN));
        Usuario u = Usuario.criar("a@sep.test", "$2a$h", Role.ADMIN);
        when(listarUsuariosUseCase.executar()).thenReturn(List.of(u));
        when(mapper.toResponse(u)).thenReturn(fakeResponse(u.getId(), Role.ADMIN));

        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    @Test
    void clienteListandoTodosRetorna403() throws Exception {
        autenticar(new UsuarioAutenticado(UUID.randomUUID(), "cliente@sep.test", Role.CLIENTE));

        mockMvc.perform(get("/api/v1/usuarios")).andExpect(status().isForbidden());
    }

    @Test
    void requestSemAutenticacaoEmRotaProtegidaRetorna401Ou403() throws Exception {
        // Sem auth no SecurityContext, @PreAuthorize("isAuthenticated()") falha com
        // AuthenticationCredentialsNotFoundException -> 401 (mapeado pelo ApiExceptionHandler).
        mockMvc.perform(get("/api/v1/usuarios/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
