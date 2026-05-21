package com.dynamis.sep_api.contratos.web.controller;

import com.dynamis.sep_api.contratos.application.port.out.exception.AssinaturaProviderException;
import com.dynamis.sep_api.contratos.application.port.out.exception.AssinaturaProviderHttpException;
import com.dynamis.sep_api.contratos.application.service.ccb.CcbGeracaoException;
import com.dynamis.sep_api.contratos.application.usecase.BaixarDocumentoAssinadoUseCase;
import com.dynamis.sep_api.contratos.application.usecase.CancelarContratoUseCase;
import com.dynamis.sep_api.contratos.application.usecase.ConsultarContratoUseCase;
import com.dynamis.sep_api.contratos.application.usecase.ConsultarStatusAssinaturaUseCase;
import com.dynamis.sep_api.contratos.application.usecase.EnviarParaAssinaturaUseCase;
import com.dynamis.sep_api.contratos.application.usecase.RegistrarAceiteUseCase;
import com.dynamis.sep_api.contratos.domain.exception.ContratoAssinaturaIndisponivelException;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.DocumentoAssinado;
import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.web.dto.StatusAssinaturaResponse;
import com.dynamis.sep_api.contratos.web.mapper.ContratoWebMapper;
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
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ContratoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    ApiExceptionHandler.class,
    ContratoAssinaturaControllerTest.MethodSecurityTestConfig.class,
    StepUpEnforcementAspect.class
})
class ContratoAssinaturaControllerTest {

    private static final String HASH = "a".repeat(64);

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    @EnableAspectJAutoProxy
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConsultarContratoUseCase consultarContratoUseCase;

    @MockBean
    private RegistrarAceiteUseCase registrarAceiteUseCase;

    @MockBean
    private CancelarContratoUseCase cancelarContratoUseCase;

    @MockBean
    private EnviarParaAssinaturaUseCase enviarParaAssinaturaUseCase;

    @MockBean
    private ConsultarStatusAssinaturaUseCase consultarStatusAssinaturaUseCase;

    @MockBean
    private BaixarDocumentoAssinadoUseCase baixarDocumentoAssinadoUseCase;

    @MockBean
    private ContratoWebMapper mapper;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private StepUpTokenService stepUpTokenService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    private UsuarioAutenticado autenticar(UUID id, Role role) {
        UsuarioAutenticado p = new UsuarioAutenticado(id, "user@sep.test", role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        Usuario u = Usuario.criar("user@sep.test", "hash", role);
        u.marcarMfaHabilitado();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
        return p;
    }

    private Contrato contratoDe(UUID tomadorId) {
        Contrato c = Contrato.criar(UUID.randomUUID(), tomadorId, TipoContrato.MUTUO);
        c.adicionarVersao("conteudo", HASH);
        return c;
    }

    // ============== POST /{id}/assinar ==============

    @Test
    void postAssinarSemAutenticacao401() throws Exception {
        mockMvc.perform(post("/api/v1/contratos/{id}/assinar", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postAssinarComClienteRetorna403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);

        mockMvc.perform(post("/api/v1/contratos/{id}/assinar", UUID.randomUUID())
                        .header("X-Step-Up-Token", "tok"))
                .andExpect(status().isForbidden());

        verify(enviarParaAssinaturaUseCase, never()).executar(any(), anyString());
    }

    @Test
    void postAssinarSemStepUpRetorna403() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);

        mockMvc.perform(post("/api/v1/contratos/{id}/assinar", UUID.randomUUID()))
                .andExpect(status().isForbidden());

        verify(enviarParaAssinaturaUseCase, never()).executar(any(), anyString());
    }

    @Test
    void postAssinarComFinanceiroEStepUp202() throws Exception {
        UsuarioAutenticado p = autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        when(stepUpTokenService.validarEConsumir(anyString())).thenReturn(Optional.of(p.id()));
        UUID contratoId = UUID.randomUUID();
        ConsultarStatusAssinaturaUseCase.StatusAssinaturaContrato snapshot =
                new ConsultarStatusAssinaturaUseCase.StatusAssinaturaContrato(
                        StatusFormalizacao.EM_ASSINATURA, StatusEnvelope.ENVIADO, "ext-1", OffsetDateTime.now());
        when(consultarStatusAssinaturaUseCase.executar(contratoId)).thenReturn(snapshot);
        when(mapper.toStatusAssinaturaResponse(snapshot))
                .thenReturn(new StatusAssinaturaResponse("EM_ASSINATURA", "ENVIADO", "ext-1", OffsetDateTime.now()));

        mockMvc.perform(post("/api/v1/contratos/{id}/assinar", contratoId).header("X-Step-Up-Token", "tok"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statusContrato").value("EM_ASSINATURA"))
                .andExpect(jsonPath("$.statusEnvelope").value("ENVIADO"));

        verify(enviarParaAssinaturaUseCase).executar(eq(contratoId), anyString());
    }

    // ============== GET /{id}/assinatura/status ==============

    @Test
    void getStatusSemAutenticacao401() throws Exception {
        mockMvc.perform(get("/api/v1/contratos/{id}/assinatura/status", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getStatusComClienteNaoOwner403() throws Exception {
        UsuarioAutenticado p = autenticar(UUID.randomUUID(), Role.CLIENTE);
        Contrato outro = contratoDe(UUID.randomUUID());
        when(consultarContratoUseCase.porId(outro.getId())).thenReturn(outro);

        mockMvc.perform(get("/api/v1/contratos/{id}/assinatura/status", outro.getId()))
                .andExpect(status().isForbidden());

        verify(consultarStatusAssinaturaUseCase, never()).executar(any());
    }

    @Test
    void getStatusComClienteOwner200() throws Exception {
        UsuarioAutenticado p = autenticar(UUID.randomUUID(), Role.CLIENTE);
        Contrato c = contratoDe(p.id());
        when(consultarContratoUseCase.porId(c.getId())).thenReturn(c);
        ConsultarStatusAssinaturaUseCase.StatusAssinaturaContrato snapshot =
                new ConsultarStatusAssinaturaUseCase.StatusAssinaturaContrato(
                        StatusFormalizacao.ACEITO, null, null, null);
        when(consultarStatusAssinaturaUseCase.executar(c.getId())).thenReturn(snapshot);
        when(mapper.toStatusAssinaturaResponse(snapshot))
                .thenReturn(new StatusAssinaturaResponse("ACEITO", null, null, null));

        mockMvc.perform(get("/api/v1/contratos/{id}/assinatura/status", c.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusContrato").value("ACEITO"))
                .andExpect(jsonPath("$.statusEnvelope").doesNotExist());
    }

    @Test
    void getStatusComFinanceiroOutroDono200() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        Contrato c = contratoDe(UUID.randomUUID());
        when(consultarContratoUseCase.porId(c.getId())).thenReturn(c);
        ConsultarStatusAssinaturaUseCase.StatusAssinaturaContrato snapshot =
                new ConsultarStatusAssinaturaUseCase.StatusAssinaturaContrato(
                        StatusFormalizacao.EM_ASSINATURA, StatusEnvelope.VISUALIZADO, "ext-x", OffsetDateTime.now());
        when(consultarStatusAssinaturaUseCase.executar(c.getId())).thenReturn(snapshot);
        when(mapper.toStatusAssinaturaResponse(snapshot))
                .thenReturn(
                        new StatusAssinaturaResponse("EM_ASSINATURA", "VISUALIZADO", "ext-x", OffsetDateTime.now()));

        mockMvc.perform(get("/api/v1/contratos/{id}/assinatura/status", c.getId()))
                .andExpect(status().isOk());
    }

    // ============== GET /{id}/documento-assinado ==============

    @Test
    void getDocumentoAssinadoSemAutenticacao401() throws Exception {
        mockMvc.perform(get("/api/v1/contratos/{id}/documento-assinado", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDocumentoAssinadoClienteNaoOwner403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        Contrato outro = contratoDe(UUID.randomUUID());
        when(consultarContratoUseCase.porId(outro.getId())).thenReturn(outro);

        mockMvc.perform(get("/api/v1/contratos/{id}/documento-assinado", outro.getId()))
                .andExpect(status().isForbidden());

        verify(baixarDocumentoAssinadoUseCase, never()).executar(any());
    }

    @Test
    void getDocumentoAssinadoClienteOwnerRetornaPdfComHeaders() throws Exception {
        UsuarioAutenticado p = autenticar(UUID.randomUUID(), Role.CLIENTE);
        Contrato c = contratoDe(p.id());
        when(consultarContratoUseCase.porId(c.getId())).thenReturn(c);
        byte[] pdf = "PDF-CONTENT".getBytes();
        DocumentoAssinado doc = DocumentoAssinado.criar(
                UUID.randomUUID(),
                HASH,
                OffsetDateTime.now(),
                null,
                UUID.randomUUID().toString());
        when(baixarDocumentoAssinadoUseCase.executar(c.getId()))
                .thenReturn(new BaixarDocumentoAssinadoUseCase.Resultado(doc, pdf));

        mockMvc.perform(get("/api/v1/contratos/{id}/documento-assinado", c.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Document-Hash-Sha256", HASH))
                .andExpect(header().string(
                                "Content-Disposition",
                                "attachment; filename=\"contrato-" + c.getId() + "-assinado.pdf\""))
                .andExpect(content().bytes(pdf));
    }

    // ============== Error mapping (review manual M1/M2) ==============

    @Test
    void getStatusComUuidInvalidoRetorna400() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);

        mockMvc.perform(get("/api/v1/contratos/{id}/assinatura/status", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(consultarStatusAssinaturaUseCase, never()).executar(any());
    }

    @Test
    void postAssinarFalhaCcbRetorna422() throws Exception {
        UsuarioAutenticado p = autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        when(stepUpTokenService.validarEConsumir(anyString())).thenReturn(Optional.of(p.id()));
        UUID contratoId = UUID.randomUUID();
        when(enviarParaAssinaturaUseCase.executar(eq(contratoId), anyString()))
                .thenThrow(new CcbGeracaoException("Falha ao gerar PDF da CCB", new RuntimeException("io")));

        mockMvc.perform(post("/api/v1/contratos/{id}/assinar", contratoId).header("X-Step-Up-Token", "tok"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void postAssinarProviderServerErrorRetorna503() throws Exception {
        UsuarioAutenticado p = autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        when(stepUpTokenService.validarEConsumir(anyString())).thenReturn(Optional.of(p.id()));
        UUID contratoId = UUID.randomUUID();
        when(enviarParaAssinaturaUseCase.executar(eq(contratoId), anyString()))
                .thenThrow(new AssinaturaProviderHttpException(503, "Clicksign indisponivel", null));

        mockMvc.perform(post("/api/v1/contratos/{id}/assinar", contratoId).header("X-Step-Up-Token", "tok"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void postAssinarProviderClientErrorRetorna422() throws Exception {
        UsuarioAutenticado p = autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        when(stepUpTokenService.validarEConsumir(anyString())).thenReturn(Optional.of(p.id()));
        UUID contratoId = UUID.randomUUID();
        when(enviarParaAssinaturaUseCase.executar(eq(contratoId), anyString()))
                .thenThrow(new AssinaturaProviderHttpException(400, "Bad request no Clicksign", null));

        mockMvc.perform(post("/api/v1/contratos/{id}/assinar", contratoId).header("X-Step-Up-Token", "tok"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void postAssinarProviderErroGenericoRetorna502() throws Exception {
        UsuarioAutenticado p = autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        when(stepUpTokenService.validarEConsumir(anyString())).thenReturn(Optional.of(p.id()));
        UUID contratoId = UUID.randomUUID();
        when(enviarParaAssinaturaUseCase.executar(eq(contratoId), anyString()))
                .thenThrow(new AssinaturaProviderException("Resposta inesperada"));

        mockMvc.perform(post("/api/v1/contratos/{id}/assinar", contratoId).header("X-Step-Up-Token", "tok"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void getDocumentoAssinadoIndisponivelRetorna409() throws Exception {
        UsuarioAutenticado p = autenticar(UUID.randomUUID(), Role.CLIENTE);
        Contrato c = contratoDe(p.id());
        when(consultarContratoUseCase.porId(c.getId())).thenReturn(c);
        when(baixarDocumentoAssinadoUseCase.executar(c.getId()))
                .thenThrow(new ContratoAssinaturaIndisponivelException(c.getId(), "status=ACEITO"));

        mockMvc.perform(get("/api/v1/contratos/{id}/documento-assinado", c.getId()))
                .andExpect(status().isConflict());
    }
}
