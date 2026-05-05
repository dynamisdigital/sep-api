package com.dynamis.sep_api.identity.web.controller;

import com.dynamis.sep_api.identity.application.usecase.AutenticarUsuarioUseCase;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.identity.web.dto.LoginRequestDto;
import com.dynamis.sep_api.identity.web.dto.TokenResponseDto;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AutenticarUsuarioUseCase autenticarUsuarioUseCase;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private UsuarioMapper mapper;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void postLoginRetorna200ComTokenResponse() throws Exception {
        UsuarioResponseDto usuario = new UsuarioResponseDto(
                UUID.randomUUID(),
                "admin@sep.test",
                Role.ADMIN,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "system",
                "system");
        TokenResponseDto resp = new TokenResponseDto("token-jwt", "Bearer", 3600, usuario);
        when(autenticarUsuarioUseCase.executar(any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDto("admin@sep.test", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.usuario.password").doesNotExist());
    }

    @Test
    void postLoginCredenciaisInvalidasRetorna401() throws Exception {
        when(autenticarUsuarioUseCase.executar(any())).thenThrow(new BadCredentialsException("Credenciais invalidas"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDto("admin@sep.test", "errada"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getMeRetornaUsuarioDoPrincipal() throws Exception {
        UUID id = UUID.randomUUID();
        UsuarioAutenticado principal = new UsuarioAutenticado(id, "admin@sep.test", Role.ADMIN);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            Usuario usuario = Usuario.criar("admin@sep.test", "$2a$hash", Role.ADMIN);
            UsuarioResponseDto response = new UsuarioResponseDto(
                    id, "admin@sep.test", Role.ADMIN, OffsetDateTime.now(), OffsetDateTime.now(), "system", "system");
            when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));
            when(mapper.toResponse(usuario)).thenReturn(response);

            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.username").value("admin@sep.test"))
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
