package com.dynamis.sep_api.contratos.web.controller;

import com.dynamis.sep_api.contratos.application.usecase.CancelarContratoUseCase;
import com.dynamis.sep_api.contratos.application.usecase.ConsultarContratoUseCase;
import com.dynamis.sep_api.contratos.application.usecase.RegistrarAceiteUseCase;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.web.dto.CancelarContratoRequest;
import com.dynamis.sep_api.contratos.web.dto.ContratoResponse;
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
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ContratoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, ContratoControllerTest.MethodSecurityTestConfig.class, StepUpEnforcementAspect.class
})
class ContratoControllerTest {

    private static final String HASH = "0".repeat(64);

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    @EnableAspectJAutoProxy
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConsultarContratoUseCase consultarContratoUseCase;

    @MockBean
    private RegistrarAceiteUseCase registrarAceiteUseCase;

    @MockBean
    private CancelarContratoUseCase cancelarContratoUseCase;

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

    private void autenticar(UUID id, Role role) {
        autenticar(id, role, false);
    }

    private void autenticar(UUID id, Role role, boolean mfaHabilitado) {
        UsuarioAutenticado p = new UsuarioAutenticado(id, "user@sep.test", role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        Usuario u = Usuario.criar("user@sep.test", "hash", role);
        if (mfaHabilitado) {
            u.marcarMfaHabilitado();
        }
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
    }

    private Contrato contratoDe(UUID tomadorId) {
        Contrato c = Contrato.criar(UUID.randomUUID(), tomadorId, TipoContrato.MUTUO);
        c.adicionarVersao("conteudo", HASH);
        return c;
    }

    // ============== GET /{id} ==============

    @Test
    void getContratoSemAutenticacao401() throws Exception {
        mockMvc.perform(get("/api/v1/contratos/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void getContratoClienteDono200() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        Contrato c = contratoDe(tomadorId);
        when(consultarContratoUseCase.porId(c.getId())).thenReturn(c);
        when(consultarContratoUseCase.buscarAceiteDaVersaoVigente(c)).thenReturn(Optional.empty());
        when(mapper.toResponse(any(), any())).thenReturn(stubResponse(c));

        mockMvc.perform(get("/api/v1/contratos/{id}", c.getId())).andExpect(status().isOk());
    }

    @Test
    void getContratoClienteAlheio403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        Contrato c = contratoDe(UUID.randomUUID());
        when(consultarContratoUseCase.porId(c.getId())).thenReturn(c);

        mockMvc.perform(get("/api/v1/contratos/{id}", c.getId())).andExpect(status().isForbidden());
    }

    @Test
    void getContratoFinanceiroAcessaQualquer200() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        Contrato c = contratoDe(UUID.randomUUID());
        when(consultarContratoUseCase.porId(c.getId())).thenReturn(c);
        when(consultarContratoUseCase.buscarAceiteDaVersaoVigente(c)).thenReturn(Optional.empty());
        when(mapper.toResponse(any(), any())).thenReturn(stubResponse(c));

        mockMvc.perform(get("/api/v1/contratos/{id}", c.getId())).andExpect(status().isOk());
    }

    @Test
    void getContratoAdminAcessaQualquer200() throws Exception {
        autenticar(UUID.randomUUID(), Role.ADMIN);
        Contrato c = contratoDe(UUID.randomUUID());
        when(consultarContratoUseCase.porId(c.getId())).thenReturn(c);
        when(consultarContratoUseCase.buscarAceiteDaVersaoVigente(c)).thenReturn(Optional.empty());
        when(mapper.toResponse(any(), any())).thenReturn(stubResponse(c));

        mockMvc.perform(get("/api/v1/contratos/{id}", c.getId())).andExpect(status().isOk());
    }

    // ============== GET /proposta/{propostaId} ==============

    @Test
    void getContratoPorPropostaClienteDono200() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        Contrato c = contratoDe(tomadorId);
        when(consultarContratoUseCase.porPropostaId(c.getPropostaId())).thenReturn(c);
        when(consultarContratoUseCase.buscarAceiteDaVersaoVigente(c)).thenReturn(Optional.empty());
        when(mapper.toResponse(any(), any())).thenReturn(stubResponse(c));

        mockMvc.perform(get("/api/v1/contratos/proposta/{id}", c.getPropostaId()))
                .andExpect(status().isOk());
    }

    @Test
    void getContratoPorPropostaClienteAlheio403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        Contrato c = contratoDe(UUID.randomUUID());
        when(consultarContratoUseCase.porPropostaId(c.getPropostaId())).thenReturn(c);

        mockMvc.perform(get("/api/v1/contratos/proposta/{id}", c.getPropostaId()))
                .andExpect(status().isForbidden());
    }

    // ============== GET /{id}/versoes ==============

    @Test
    void getVersoesClienteDono200() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        Contrato c = contratoDe(tomadorId);
        when(consultarContratoUseCase.porId(c.getId())).thenReturn(c);
        when(consultarContratoUseCase.listarVersoes(c.getId())).thenReturn(c.getVersoes());
        when(mapper.toVersaoListResponse(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/contratos/{id}/versoes", c.getId())).andExpect(status().isOk());
    }

    @Test
    void getVersoesClienteAlheio403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        Contrato c = contratoDe(UUID.randomUUID());
        when(consultarContratoUseCase.porId(c.getId())).thenReturn(c);

        mockMvc.perform(get("/api/v1/contratos/{id}/versoes", c.getId())).andExpect(status().isForbidden());
    }

    // ============== PATCH /{id}/aceite ==============

    @Test
    void patchAceiteSemAutenticacao401() throws Exception {
        mockMvc.perform(patch("/api/v1/contratos/{id}/aceite", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchAceiteComoFinanceiro403() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        mockMvc.perform(patch("/api/v1/contratos/{id}/aceite", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchAceiteClienteSemStepUp403() throws Exception {
        // MFA habilitado mas sem X-Step-Up-Token -> StepUpEnforcementAspect bloqueia
        autenticar(UUID.randomUUID(), Role.CLIENTE, true);
        mockMvc.perform(patch("/api/v1/contratos/{id}/aceite", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchAceiteClienteComStepUp200() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE, true);
        Contrato c = contratoDe(tomadorId);
        String token = "step-up-token-valido";
        when(stepUpTokenService.validarEConsumir(token)).thenReturn(Optional.of(tomadorId));
        when(registrarAceiteUseCase.executar(any())).thenReturn(c);
        when(consultarContratoUseCase.buscarAceiteDaVersaoVigente(c)).thenReturn(Optional.empty());
        when(mapper.toResponse(any(), any())).thenReturn(stubResponse(c));

        mockMvc.perform(patch("/api/v1/contratos/{id}/aceite", c.getId()).header("X-Step-Up-Token", token))
                .andExpect(status().isOk());
    }

    // ============== POST /{id}/cancelar ==============

    @Test
    void postCancelarComoCliente403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        CancelarContratoRequest req = new CancelarContratoRequest("Justificativa adequada do operador.");
        mockMvc.perform(post("/api/v1/contratos/{id}/cancelar", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postCancelarFinanceiroSemStepUp403() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO, true);
        CancelarContratoRequest req = new CancelarContratoRequest("Justificativa adequada do operador.");
        mockMvc.perform(post("/api/v1/contratos/{id}/cancelar", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postCancelarFinanceiroComStepUp200() throws Exception {
        UUID financeiroId = UUID.randomUUID();
        autenticar(financeiroId, Role.FINANCEIRO, true);
        Contrato c = contratoDe(UUID.randomUUID());
        String token = "step-up-token-valido";
        when(stepUpTokenService.validarEConsumir(token)).thenReturn(Optional.of(financeiroId));
        when(cancelarContratoUseCase.executar(any())).thenReturn(c);
        when(consultarContratoUseCase.buscarAceiteDaVersaoVigente(c)).thenReturn(Optional.empty());
        when(mapper.toResponse(any(), any())).thenReturn(stubResponse(c));

        CancelarContratoRequest req = new CancelarContratoRequest("Justificativa adequada do operador.");
        mockMvc.perform(post("/api/v1/contratos/{id}/cancelar", c.getId())
                        .header("X-Step-Up-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void postCancelarPayloadInvalido400() throws Exception {
        UUID financeiroId = UUID.randomUUID();
        autenticar(financeiroId, Role.FINANCEIRO, true);
        String token = "step-up-token-valido";
        when(stepUpTokenService.validarEConsumir(token)).thenReturn(Optional.of(financeiroId));

        mockMvc.perform(post("/api/v1/contratos/{id}/cancelar", UUID.randomUUID())
                        .header("X-Step-Up-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificativa\":\"curta\"}"))
                .andExpect(status().isBadRequest());
    }

    private ContratoResponse stubResponse(Contrato c) {
        return new ContratoResponse(
                c.getId(),
                c.getPropostaId(),
                c.getTomadorId(),
                c.getTipo().name(),
                c.getStatus().name(),
                null,
                null,
                null,
                null);
    }
}
