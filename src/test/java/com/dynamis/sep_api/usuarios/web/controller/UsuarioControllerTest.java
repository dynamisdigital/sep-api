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
    private com.dynamis.sep_api.usuarios.application.usecase.AlterarRoleUsuarioUseCase alterarRoleUsuarioUseCase;

    @MockBean
    private com.dynamis.sep_api.usuarios.application.usecase.GerenciarRolesUsuarioUseCase gerenciarRolesUsuarioUseCase;

    @MockBean
    private UsuarioMapper mapper;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void postPublicoCriaClienteRetorna201ComLocationESemPassword() throws Exception {
        Usuario salvo = Usuario.criar("cliente@sep.test", "$2a$10$h", Role.CLIENTE);
        UsuarioResponseDto response = new UsuarioResponseDto(
                salvo.getId(),
                "cliente@sep.test",
                Role.CLIENTE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "system",
                "system",
                false,
                false);
        when(criarUsuarioUseCase.executar(any(UsuarioCreateDto.class))).thenReturn(salvo);
        when(mapper.toResponse(salvo)).thenReturn(response);

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UsuarioCreateDto("cliente@sep.test", "senha-passphrase-segura"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/usuarios/")))
                .andExpect(jsonPath("$.username").value("cliente@sep.test"))
                .andExpect(jsonPath("$.role").value("CLIENTE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(containsString("\"id\":")))
                .andExpect(content().string(containsString("\"criadoPor\":\"system\"")));
    }

    @Test
    void postPublicoIgnoraRoleAdminNoPayloadECriaCliente() throws Exception {
        // 5F-FIX-01: payload com role=ADMIN nao escala privilegio; campo e ignorado e sempre vira CLIENTE.
        Usuario salvo = Usuario.criar("hacker@sep.test", "$2a$10$h", Role.CLIENTE);
        UsuarioResponseDto response = new UsuarioResponseDto(
                salvo.getId(),
                "hacker@sep.test",
                Role.CLIENTE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "system",
                "system",
                false,
                false);
        when(criarUsuarioUseCase.executar(any(UsuarioCreateDto.class))).thenReturn(salvo);
        when(mapper.toResponse(salvo)).thenReturn(response);

        String body =
                """
                {"username":"hacker@sep.test","password":"senha-passphrase-segura","role":"ADMIN"}
                """;

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLIENTE"));
    }

    @Test
    void postComUsernameInvalidoRetorna400() throws Exception {
        String body = """
            {"username":"nao-eh-email","password":"senha-passphrase-segura"}
            """;

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void postComPasswordVazioRetorna400() throws Exception {
        String body = """
            {"username":"cliente@sep.test","password":""}
            """;

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void postComUsernameJaExistenteRetorna409() throws Exception {
        when(criarUsuarioUseCase.executar(any(UsuarioCreateDto.class)))
                .thenThrow(new UsernameJaExisteException("cliente@sep.test"));

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UsuarioCreateDto("cliente@sep.test", "senha-passphrase-segura"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("cliente@sep.test")));
    }
}
