package com.dynamis.sep_api.onboarding.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.ApiAccessDeniedHandler;
import com.dynamis.sep_api.identity.infrastructure.security.ApiAuthenticationEntryPoint;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.onboarding.application.dto.StatusOnboardingView;
import com.dynamis.sep_api.onboarding.application.usecase.ConsultarStatusOnboardingUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.EnviarDocumentoUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.IniciarOnboardingPessoaUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.IniciarVerificacaoKycUseCase;
import com.dynamis.sep_api.onboarding.domain.exception.CpfComOnboardingAtivoException;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.web.mapper.OnboardingWebMapper;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OnboardingPessoaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, OnboardingPessoaControllerTest.WebMapperConfig.class})
class OnboardingPessoaControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class WebMapperConfig {
        @org.springframework.context.annotation.Bean
        OnboardingWebMapper onboardingWebMapper() {
            return new OnboardingWebMapper() {};
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IniciarOnboardingPessoaUseCase iniciarUseCase;

    @MockBean
    private EnviarDocumentoUseCase enviarDocumentoUseCase;

    @MockBean
    private IniciarVerificacaoKycUseCase iniciarVerificacaoUseCase;

    @MockBean
    private ConsultarStatusOnboardingUseCase consultarStatusUseCase;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

    @MockBean
    private ApiAccessDeniedHandler apiAccessDeniedHandler;

    private UUID usuarioId;
    private UUID solicitacaoId;
    private SolicitacaoOnboarding solicitacao;

    @BeforeEach
    void setup() {
        usuarioId = UUID.randomUUID();
        solicitacao = SolicitacaoOnboarding.criar(usuarioId, new Cpf("52998224725"), "Joao", LocalDate.of(1990, 1, 1));
        solicitacaoId = solicitacao.getId();
        autenticar(usuarioId, Role.CLIENTE);
    }

    private void autenticar(UUID id, Role role) {
        UsuarioAutenticado principal = new UsuarioAutenticado(id, "user@sep.test", role);
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @WithMockUser
    void iniciarRetorna201ComLocationHeader() throws Exception {
        when(iniciarUseCase.executar(eq(usuarioId), anyString(), anyString(), any()))
                .thenReturn(solicitacao);

        mockMvc.perform(post("/api/v1/onboarding/pessoa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cpf", "52998224725",
                                "nomeCompleto", "Joao",
                                "dataNascimento", "1990-01-01"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/onboarding/pessoa/" + solicitacaoId))
                .andExpect(jsonPath("$.id").value(solicitacaoId.toString()))
                .andExpect(jsonPath("$.status").value("INICIADO"));
    }

    @Test
    @WithMockUser
    void iniciarRetorna400EmCpfFaltando() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/pessoa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeCompleto\":\"Joao\",\"dataNascimento\":\"1990-01-01\"}"))
                .andExpect(status().isBadRequest());
        verify(iniciarUseCase, never()).executar(any(), anyString(), anyString(), any());
    }

    @Test
    @WithMockUser
    void iniciarRetorna409QuandoCpfDuplicado() throws Exception {
        when(iniciarUseCase.executar(any(), anyString(), anyString(), any()))
                .thenThrow(new CpfComOnboardingAtivoException());

        mockMvc.perform(post("/api/v1/onboarding/pessoa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cpf", "52998224725",
                                "nomeCompleto", "Joao",
                                "dataNascimento", "1990-01-01"))))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void enviarDocumentoRetorna204ComMultipart() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "rg.jpg", "image/jpeg", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/v1/onboarding/pessoa/{id}/documentos", solicitacaoId)
                        .file(arquivo)
                        .param("tipo", "RG"))
                .andExpect(status().isNoContent());

        verify(enviarDocumentoUseCase).executar(eq(solicitacaoId), eq(usuarioId), any());
    }

    @Test
    @WithMockUser
    void enviarDocumentoRetorna400QuandoArquivoVazio() throws Exception {
        MockMultipartFile vazio = new MockMultipartFile("arquivo", "rg.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/v1/onboarding/pessoa/{id}/documentos", solicitacaoId)
                        .file(vazio)
                        .param("tipo", "RG"))
                .andExpect(status().isBadRequest());
        verify(enviarDocumentoUseCase, never()).executar(any(), any(), any());
    }

    @Test
    @WithMockUser
    void verificarRetorna202() throws Exception {
        when(iniciarVerificacaoUseCase.executar(eq(solicitacaoId), eq(usuarioId), any()))
                .thenReturn(solicitacao);

        mockMvc.perform(post("/api/v1/onboarding/pessoa/{id}/verificar", solicitacaoId))
                .andExpect(status().isAccepted());

        verify(iniciarVerificacaoUseCase).executar(eq(solicitacaoId), eq(usuarioId), any());
    }

    @Test
    @WithMockUser
    void consultarRetorna200ComStatusEDocumentos() throws Exception {
        StatusOnboardingView view = new StatusOnboardingView(
                solicitacaoId,
                StatusOnboarding.DOCUMENTOS_RECEBIDOS,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of(new StatusOnboardingView.DocumentoEnviado(
                        UUID.randomUUID(),
                        com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento.RG,
                        OffsetDateTime.now(),
                        "h1")),
                null);
        when(consultarStatusUseCase.executar(eq(solicitacaoId), eq(usuarioId), anyBoolean()))
                .thenReturn(view);

        mockMvc.perform(get("/api/v1/onboarding/pessoa/{id}", solicitacaoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOCUMENTOS_RECEBIDOS"))
                .andExpect(jsonPath("$.documentosEnviados[0].tipo").value("RG"));
    }

    @Test
    @WithMockUser
    void consultarPropagaIsAdminTrueParaADMIN() throws Exception {
        autenticar(UUID.randomUUID(), Role.ADMIN);
        StatusOnboardingView view = new StatusOnboardingView(
                solicitacaoId, StatusOnboarding.APROVADO, OffsetDateTime.now(), OffsetDateTime.now(), List.of(), null);
        when(consultarStatusUseCase.executar(eq(solicitacaoId), any(), eq(true)))
                .thenReturn(view);

        mockMvc.perform(get("/api/v1/onboarding/pessoa/{id}", solicitacaoId)).andExpect(status().isOk());

        verify(consultarStatusUseCase).executar(eq(solicitacaoId), any(), eq(true));
    }

    @Test
    @WithMockUser
    void consultarRetorna404SeUseCaseLancaNaoEncontrada() throws Exception {
        when(consultarStatusUseCase.executar(any(), any(), anyBoolean()))
                .thenThrow(new OnboardingNaoEncontradoException(solicitacaoId));

        mockMvc.perform(get("/api/v1/onboarding/pessoa/{id}", solicitacaoId)).andExpect(status().isNotFound());
    }
}
