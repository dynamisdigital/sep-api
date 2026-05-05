package com.dynamis.sep_api.usuarios.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.usuarios.application.exception.UsernameJaExisteException;
import com.dynamis.sep_api.usuarios.application.usecase.AlterarSenhaUseCase;
import com.dynamis.sep_api.usuarios.application.usecase.ConsultarUsuarioUseCase;
import com.dynamis.sep_api.usuarios.application.usecase.CriarUsuarioUseCase;
import com.dynamis.sep_api.usuarios.application.usecase.ListarUsuariosUseCase;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioCreateDto;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class UsuarioControllerTest {

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
    private UsuarioMapper mapper;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void postValidoRetorna201ComLocationESemPassword() throws Exception {
        Usuario salvo = Usuario.criar("admin@sep.test", "$2a$10$h", Role.ADMIN);
        UsuarioResponseDto response = new UsuarioResponseDto(
                salvo.getId(),
                "admin@sep.test",
                Role.ADMIN,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "system",
                "system");
        when(criarUsuarioUseCase.executar(any(UsuarioCreateDto.class))).thenReturn(salvo);
        when(mapper.toResponse(salvo)).thenReturn(response);

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UsuarioCreateDto("admin@sep.test", "123456", Role.ADMIN))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/usuarios/")))
                .andExpect(jsonPath("$.username").value("admin@sep.test"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(containsString("\"id\":")))
                .andExpect(content().string(containsString("\"criadoPor\":\"system\"")));
    }

    @Test
    void postComUsernameInvalidoRetorna400() throws Exception {
        String body = """
            {"username":"nao-eh-email","password":"123456","role":"ADMIN"}
            """;

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void postComSenhaTamanhoInvalidoRetorna400() throws Exception {
        String body = """
            {"username":"admin@sep.test","password":"12345","role":"ADMIN"}
            """;

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void postComRoleInvalidoRetorna400() throws Exception {
        String body =
                """
            {"username":"admin@sep.test","password":"123456","role":"SUPER_USER"}
            """;

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postComUsernameJaExistenteRetorna409() throws Exception {
        when(criarUsuarioUseCase.executar(any(UsuarioCreateDto.class)))
                .thenThrow(new UsernameJaExisteException("admin@sep.test"));

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UsuarioCreateDto("admin@sep.test", "123456", Role.ADMIN))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("admin@sep.test")));
    }
}
