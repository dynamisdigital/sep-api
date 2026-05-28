package com.dynamis.sep_api.usuarios.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.usuarios.application.exception.SenhaAtualIncorretaException;
import com.dynamis.sep_api.usuarios.application.usecase.AlterarSenhaUseCase;
import com.dynamis.sep_api.usuarios.application.usecase.ConsultarUsuarioUseCase;
import com.dynamis.sep_api.usuarios.application.usecase.CriarUsuarioUseCase;
import com.dynamis.sep_api.usuarios.application.usecase.ListarUsuariosUseCase;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioSenhaUpdateDto;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, UsuarioControllerSenhaTest.MethodSecurityTestConfig.class})
class UsuarioControllerSenhaTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CriarUsuarioUseCase criarUsuarioUseCase;

    @MockBean
    private ConsultarUsuarioUseCase consultarUsuarioUseCase;

    @MockBean
    private ListarUsuariosUseCase listarUsuariosUseCase;

    @MockBean
    private AlterarSenhaUseCase alterarSenhaUseCase;

    @MockBean
    private com.dynamis.sep_api.usuarios.application.usecase.AlterarRoleUsuarioUseCase alterarRoleUsuarioUseCase;

    @MockBean
    private com.dynamis.sep_api.usuarios.application.usecase.GerenciarRolesUsuarioUseCase gerenciarRolesUsuarioUseCase;

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

    @Test
    void patchSenhaProprioRetorna204() throws Exception {
        UUID id = UUID.randomUUID();
        autenticar(new UsuarioAutenticado(id, "a@sep.test", Role.CLIENTE));
        doNothing().when(alterarSenhaUseCase).executar(eq(id), any(), any());

        mockMvc.perform(patch("/api/v1/usuarios/{id}/senha", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsuarioSenhaUpdateDto("123456", "abcdef"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void patchSenhaTerceiroRetorna403() throws Exception {
        UUID proprio = UUID.randomUUID();
        UUID alheio = UUID.randomUUID();
        autenticar(new UsuarioAutenticado(proprio, "a@sep.test", Role.CLIENTE));
        doThrow(new AccessDeniedException("acesso negado"))
                .when(alterarSenhaUseCase)
                .executar(any(), any(), any());

        mockMvc.perform(patch("/api/v1/usuarios/{id}/senha", alheio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsuarioSenhaUpdateDto("123456", "abcdef"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchSenhaAtualIncorretaRetorna400() throws Exception {
        UUID id = UUID.randomUUID();
        autenticar(new UsuarioAutenticado(id, "a@sep.test", Role.CLIENTE));
        doThrow(new SenhaAtualIncorretaException()).when(alterarSenhaUseCase).executar(any(), any(), any());

        mockMvc.perform(patch("/api/v1/usuarios/{id}/senha", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsuarioSenhaUpdateDto("errada", "abcdef"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchSenhaSemAutenticacaoRetorna401() throws Exception {
        UUID id = UUID.randomUUID();
        // SecurityContext vazio: @PreAuthorize falha com AuthenticationCredentialsNotFoundException -> 401
        mockMvc.perform(patch("/api/v1/usuarios/{id}/senha", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsuarioSenhaUpdateDto("123456", "abcdef"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchSenhaInvalidaRetorna400() throws Exception {
        UUID id = UUID.randomUUID();
        autenticar(new UsuarioAutenticado(id, "a@sep.test", Role.CLIENTE));

        mockMvc.perform(patch("/api/v1/usuarios/{id}/senha", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordAtual\":\"\",\"novaSenha\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
