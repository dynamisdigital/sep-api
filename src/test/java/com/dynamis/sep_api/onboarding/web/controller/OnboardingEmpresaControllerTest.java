package com.dynamis.sep_api.onboarding.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.ApiAccessDeniedHandler;
import com.dynamis.sep_api.identity.infrastructure.security.ApiAuthenticationEntryPoint;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.onboarding.application.dto.StatusOnboardingEmpresaView;
import com.dynamis.sep_api.onboarding.application.usecase.ConsultarRepresentantesLegaisUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.ConsultarStatusOnboardingEmpresaUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.EnviarDocumentoUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.IniciarOnboardingEmpresaUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.IniciarVerificacaoKybUseCase;
import com.dynamis.sep_api.onboarding.domain.exception.CnpjComOnboardingAtivoException;
import com.dynamis.sep_api.onboarding.domain.exception.KybNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.StatusPldRepresentante;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
import com.dynamis.sep_api.onboarding.web.mapper.OnboardingEmpresaWebMapper;
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

@WebMvcTest(controllers = OnboardingEmpresaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, OnboardingEmpresaControllerTest.WebMapperConfig.class})
class OnboardingEmpresaControllerTest {

    private static final String CNPJ_VALIDO = "11222333000181";

    @org.springframework.boot.test.context.TestConfiguration
    static class WebMapperConfig {
        @org.springframework.context.annotation.Bean
        OnboardingEmpresaWebMapper onboardingEmpresaWebMapper() {
            return new OnboardingEmpresaWebMapper() {};
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IniciarOnboardingEmpresaUseCase iniciarUseCase;

    @MockBean
    private EnviarDocumentoUseCase enviarDocumentoUseCase;

    @MockBean
    private IniciarVerificacaoKybUseCase iniciarVerificacaoUseCase;

    @MockBean
    private ConsultarStatusOnboardingEmpresaUseCase consultarStatusUseCase;

    @MockBean
    private ConsultarRepresentantesLegaisUseCase consultarRepresentantesUseCase;

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
    private KybEmpresa kyb;

    @BeforeEach
    void setup() {
        usuarioId = UUID.randomUUID();
        solicitacao = SolicitacaoOnboarding.criarEmpresa(usuarioId, CNPJ_VALIDO, "ACME LTDA");
        solicitacaoId = solicitacao.getId();
        kyb = KybEmpresa.criar(
                solicitacaoId, new Cnpj(CNPJ_VALIDO), "ACME LTDA", "Acme", TipoSocietario.LTDA, PorteEmpresa.ME);
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
    void iniciarRetorna201ComLocationHeaderECnpjFormatado() throws Exception {
        when(iniciarUseCase.executar(eq(usuarioId), anyString(), anyString(), any(), any(), any()))
                .thenReturn(solicitacao);

        mockMvc.perform(post("/api/v1/onboarding/empresa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cnpj", CNPJ_VALIDO,
                                "razaoSocial", "ACME LTDA",
                                "nomeFantasia", "Acme",
                                "tipoSocietario", "LTDA",
                                "porte", "ME"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/onboarding/empresa/" + solicitacaoId))
                .andExpect(jsonPath("$.id").value(solicitacaoId.toString()))
                .andExpect(jsonPath("$.status").value("INICIADO"))
                .andExpect(jsonPath("$.cnpj").value("11.222.333/0001-81"))
                .andExpect(jsonPath("$.razaoSocial").value("ACME LTDA"));
    }

    @Test
    @WithMockUser
    void iniciarRetorna400EmCnpjFaltando() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/empresa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"razaoSocial\":\"ACME\"}"))
                .andExpect(status().isBadRequest());
        verify(iniciarUseCase, never()).executar(any(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    @WithMockUser
    void iniciarRetorna409QuandoCnpjDuplicado() throws Exception {
        when(iniciarUseCase.executar(any(), anyString(), anyString(), any(), any(), any()))
                .thenThrow(new CnpjComOnboardingAtivoException());

        mockMvc.perform(post("/api/v1/onboarding/empresa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("cnpj", CNPJ_VALIDO, "razaoSocial", "ACME LTDA"))))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void enviarDocumentoRetorna204ComMultipart() throws Exception {
        MockMultipartFile arquivo =
                new MockMultipartFile("arquivo", "contrato.pdf", "application/pdf", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/v1/onboarding/empresa/{id}/documentos", solicitacaoId)
                        .file(arquivo)
                        .param("tipo", "CONTRATO_SOCIAL"))
                .andExpect(status().isNoContent());

        verify(enviarDocumentoUseCase).executar(eq(solicitacaoId), eq(usuarioId), eq(false), any());
    }

    @Test
    @WithMockUser
    void enviarDocumentoRetorna400QuandoArquivoVazio() throws Exception {
        MockMultipartFile vazio = new MockMultipartFile("arquivo", "x.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/v1/onboarding/empresa/{id}/documentos", solicitacaoId)
                        .file(vazio)
                        .param("tipo", "CONTRATO_SOCIAL"))
                .andExpect(status().isBadRequest());
        verify(enviarDocumentoUseCase, never()).executar(any(), any(), anyBoolean(), any());
    }

    @Test
    @WithMockUser
    void enviarDocumentoComoAdminPassaIsAdminTrue() throws Exception {
        autenticar(UUID.randomUUID(), Role.ADMIN);
        MockMultipartFile arquivo =
                new MockMultipartFile("arquivo", "contrato.pdf", "application/pdf", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/v1/onboarding/empresa/{id}/documentos", solicitacaoId)
                        .file(arquivo)
                        .param("tipo", "CONTRATO_SOCIAL"))
                .andExpect(status().isNoContent());

        verify(enviarDocumentoUseCase).executar(eq(solicitacaoId), any(), eq(true), any());
    }

    @Test
    @WithMockUser
    void verificarRetorna202() throws Exception {
        when(iniciarVerificacaoUseCase.executar(eq(solicitacaoId), eq(usuarioId), eq(false), any()))
                .thenReturn(solicitacao);

        mockMvc.perform(post("/api/v1/onboarding/empresa/{id}/verificar", solicitacaoId))
                .andExpect(status().isAccepted());

        verify(iniciarVerificacaoUseCase).executar(eq(solicitacaoId), eq(usuarioId), eq(false), any());
    }

    @Test
    @WithMockUser
    void verificarComoAdminPassaIsAdminTrue() throws Exception {
        autenticar(UUID.randomUUID(), Role.ADMIN);
        when(iniciarVerificacaoUseCase.executar(eq(solicitacaoId), any(), eq(true), any()))
                .thenReturn(solicitacao);

        mockMvc.perform(post("/api/v1/onboarding/empresa/{id}/verificar", solicitacaoId))
                .andExpect(status().isAccepted());

        verify(iniciarVerificacaoUseCase).executar(eq(solicitacaoId), any(), eq(true), any());
    }

    @Test
    @WithMockUser
    void consultarRetorna200ComDadosEmpresaECnpjFormatado() throws Exception {
        var dados = new StatusOnboardingEmpresaView.DadosEmpresaView(
                CNPJ_VALIDO, "ACME LTDA", "Acme", TipoSocietario.LTDA, PorteEmpresa.ME);
        var documento = new StatusOnboardingEmpresaView.DocumentoEnviado(
                UUID.randomUUID(), TipoDocumento.CONTRATO_SOCIAL, OffsetDateTime.now(), "abc");
        var representante = new StatusOnboardingEmpresaView.RepresentanteView(
                UUID.randomUUID(),
                "Maria Souza",
                "52998224725",
                "Diretora",
                StatusPldRepresentante.LIMPO,
                OffsetDateTime.now());
        var view = new StatusOnboardingEmpresaView(
                solicitacaoId,
                StatusOnboarding.DOCUMENTOS_RECEBIDOS,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                dados,
                List.of(documento),
                List.of(representante),
                null);
        when(consultarStatusUseCase.executar(eq(solicitacaoId), eq(usuarioId), anyBoolean()))
                .thenReturn(view);

        mockMvc.perform(get("/api/v1/onboarding/empresa/{id}", solicitacaoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOCUMENTOS_RECEBIDOS"))
                .andExpect(jsonPath("$.dadosEmpresa.cnpj").value("11.222.333/0001-81"))
                .andExpect(jsonPath("$.dadosEmpresa.razaoSocial").value("ACME LTDA"))
                .andExpect(jsonPath("$.dadosEmpresa.tipoSocietario").value("LTDA"))
                .andExpect(jsonPath("$.documentosEnviados[0].tipo").value("CONTRATO_SOCIAL"))
                .andExpect(jsonPath("$.representantes[0].nome").value("Maria Souza"))
                .andExpect(jsonPath("$.representantes[0].cpfMascarado").value("529******25"))
                .andExpect(jsonPath("$.representantes[0].pld.statusPld").value("LIMPO"));
    }

    @Test
    @WithMockUser
    void consultarPropagaIsAdminTrueParaADMIN() throws Exception {
        autenticar(UUID.randomUUID(), Role.ADMIN);
        var dados = new StatusOnboardingEmpresaView.DadosEmpresaView(
                CNPJ_VALIDO, "ACME LTDA", null, TipoSocietario.LTDA, PorteEmpresa.ME);
        var view = new StatusOnboardingEmpresaView(
                solicitacaoId,
                StatusOnboarding.APROVADO,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                dados,
                List.of(),
                List.of(),
                null);
        when(consultarStatusUseCase.executar(eq(solicitacaoId), any(), eq(true)))
                .thenReturn(view);

        mockMvc.perform(get("/api/v1/onboarding/empresa/{id}", solicitacaoId)).andExpect(status().isOk());

        verify(consultarStatusUseCase).executar(eq(solicitacaoId), any(), eq(true));
    }

    @Test
    @WithMockUser
    void consultarRetorna404SeUseCaseLancaNaoEncontrada() throws Exception {
        when(consultarStatusUseCase.executar(any(), any(), anyBoolean()))
                .thenThrow(new OnboardingNaoEncontradoException(solicitacaoId));

        mockMvc.perform(get("/api/v1/onboarding/empresa/{id}", solicitacaoId)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void listarRepresentantesRetorna200ComCpfMascarado() throws Exception {
        RepresentanteLegal rep =
                RepresentanteLegal.criar(kyb.getId(), "Maria Souza", new Cpf("52998224725"), "Diretora");
        when(consultarRepresentantesUseCase.executar(eq(solicitacaoId), eq(usuarioId), anyBoolean()))
                .thenReturn(List.of(rep));

        mockMvc.perform(get("/api/v1/onboarding/empresa/{id}/representantes", solicitacaoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Maria Souza"))
                .andExpect(jsonPath("$[0].cpfMascarado").value("529******25"))
                .andExpect(jsonPath("$[0].pld.statusPld").value("PENDENTE"));
    }

    @Test
    @WithMockUser
    void listarRepresentantesRetorna404SeKybInexistente() throws Exception {
        when(consultarRepresentantesUseCase.executar(any(), any(), anyBoolean()))
                .thenThrow(new KybNaoEncontradoException(solicitacaoId));

        mockMvc.perform(get("/api/v1/onboarding/empresa/{id}/representantes", solicitacaoId))
                .andExpect(status().isNotFound());
    }
}
