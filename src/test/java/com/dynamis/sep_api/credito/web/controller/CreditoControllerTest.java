package com.dynamis.sep_api.credito.web.controller;

import com.dynamis.sep_api.credito.application.dto.PropostaCompletaView;
import com.dynamis.sep_api.credito.application.usecase.ConsultarPropostaCompletaUseCase;
import com.dynamis.sep_api.credito.application.usecase.CriarPropostaCreditoUseCase;
import com.dynamis.sep_api.credito.application.usecase.ListarPropostasUseCase;
import com.dynamis.sep_api.credito.application.usecase.ListarRegrasAvaliadasUseCase;
import com.dynamis.sep_api.credito.application.usecase.RegistrarParecerUseCase;
import com.dynamis.sep_api.credito.domain.model.ParecerCredito;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.web.dto.CriarPropostaRequest;
import com.dynamis.sep_api.credito.web.dto.RegistrarParecerRequest;
import com.dynamis.sep_api.credito.web.mapper.CreditoWebMapper;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CreditoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, CreditoControllerTest.MethodSecurityTestConfig.class, StepUpEnforcementAspect.class
})
class CreditoControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    @EnableAspectJAutoProxy
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CriarPropostaCreditoUseCase criarPropostaUseCase;

    @MockBean
    private ConsultarPropostaCompletaUseCase consultarPropostaCompletaUseCase;

    @MockBean
    private ListarPropostasUseCase listarPropostasUseCase;

    @MockBean
    private ListarRegrasAvaliadasUseCase listarRegrasAvaliadasUseCase;

    @MockBean
    private RegistrarParecerUseCase registrarParecerUseCase;

    @MockBean
    private CreditoWebMapper mapper;

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

    @Test
    void postPropostaSemAutenticacao401() throws Exception {
        CriarPropostaRequest req =
                new CriarPropostaRequest(UUID.randomUUID(), TipoOperacao.OUTROS, new BigDecimal("10000"), 12);
        mockMvc.perform(post("/api/v1/credito/propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postPropostaComoAdmin403() throws Exception {
        autenticar(UUID.randomUUID(), Role.ADMIN);
        CriarPropostaRequest req =
                new CriarPropostaRequest(UUID.randomUUID(), TipoOperacao.OUTROS, new BigDecimal("10000"), 12);
        mockMvc.perform(post("/api/v1/credito/propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postPropostaComoFinanceiro403() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        CriarPropostaRequest req =
                new CriarPropostaRequest(UUID.randomUUID(), TipoOperacao.OUTROS, new BigDecimal("10000"), 12);
        mockMvc.perform(post("/api/v1/credito/propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postPropostaPayloadInvalido400() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        mockMvc.perform(post("/api/v1/credito/propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorSolicitado\":0,\"prazoMeses\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postPropostaClienteRetorna201() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        PropostaCredito proposta = PropostaCredito.criar(
                tomadorId, UUID.randomUUID(), TipoOperacao.OUTROS, new Money(new BigDecimal("10000"), "BRL"), 12);
        when(criarPropostaUseCase.executar(any())).thenReturn(proposta);
        when(mapper.toResponse(any(), any(), any())).thenReturn(null);

        CriarPropostaRequest req = new CriarPropostaRequest(
                proposta.getSolicitacaoOnboardingId(), TipoOperacao.OUTROS, new BigDecimal("10000"), 12);

        mockMvc.perform(post("/api/v1/credito/propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header("Location", "/api/v1/credito/propostas/" + proposta.getId()));
    }

    @Test
    void getPropostaAlheia403ParaCliente() throws Exception {
        UUID outroTomador = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.CLIENTE);

        PropostaCredito alheia = PropostaCredito.criar(
                outroTomador, UUID.randomUUID(), TipoOperacao.OUTROS, new Money(new BigDecimal("10000"), "BRL"), 12);
        when(consultarPropostaCompletaUseCase.executar(propostaId))
                .thenReturn(new PropostaCompletaView(alheia, null, null));

        mockMvc.perform(get("/api/v1/credito/propostas/{id}", propostaId)).andExpect(status().isForbidden());
    }

    @Test
    void getPropostaFinanceiroAcessaQualquer200() throws Exception {
        UUID propostaId = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);

        PropostaCredito qualquer = PropostaCredito.criar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TipoOperacao.OUTROS,
                new Money(new BigDecimal("10000"), "BRL"),
                12);
        when(consultarPropostaCompletaUseCase.executar(propostaId))
                .thenReturn(new PropostaCompletaView(qualquer, null, null));
        when(mapper.toResponse(any(), any(), any())).thenReturn(null);

        mockMvc.perform(get("/api/v1/credito/propostas/{id}", propostaId)).andExpect(status().isOk());
    }

    @Test
    void postParecerComoCliente403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        RegistrarParecerRequest req =
                new RegistrarParecerRequest(DecisaoParecer.APROVAR, "Justificativa adequada do parecerista");

        mockMvc.perform(post("/api/v1/credito/propostas/{id}/parecer", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postParecerFinanceiroSemStepUpComMfaHabilitadoRetorna403() throws Exception {
        UUID propostaId = UUID.randomUUID();
        UUID pareceristaId = UUID.randomUUID();
        autenticar(pareceristaId, Role.FINANCEIRO, true);
        when(stepUpTokenService.validarEConsumir(null)).thenReturn(Optional.empty());

        RegistrarParecerRequest req =
                new RegistrarParecerRequest(DecisaoParecer.APROVAR, "Justificativa adequada do parecerista");
        mockMvc.perform(post("/api/v1/credito/propostas/{id}/parecer", propostaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postParecerFinanceiroComStepUpValido200() throws Exception {
        UUID propostaId = UUID.randomUUID();
        UUID pareceristaId = UUID.randomUUID();
        autenticar(pareceristaId, Role.FINANCEIRO, true);
        String token = "step-up-token-valido";
        when(stepUpTokenService.validarEConsumir(token)).thenReturn(Optional.of(pareceristaId));

        ParecerCredito parecer = ParecerCredito.registrar(
                propostaId, pareceristaId, DecisaoParecer.APROVAR, "Justificativa adequada do parecerista", 800, 1);
        when(registrarParecerUseCase.executar(
                        any(com.dynamis.sep_api.credito.application.dto.RegistrarParecerCommand.class)))
                .thenReturn(parecer);
        when(mapper.toParecerResponse(any())).thenReturn(null);

        RegistrarParecerRequest req =
                new RegistrarParecerRequest(DecisaoParecer.APROVAR, "Justificativa adequada do parecerista");
        mockMvc.perform(post("/api/v1/credito/propostas/{id}/parecer", propostaId)
                        .header("X-Step-Up-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void postParecerFinanceiroSemMfaHabilitadoPermiteSemStepUp200() throws Exception {
        UUID propostaId = UUID.randomUUID();
        UUID pareceristaId = UUID.randomUUID();
        autenticar(pareceristaId, Role.FINANCEIRO, false);

        ParecerCredito parecer = ParecerCredito.registrar(
                propostaId, pareceristaId, DecisaoParecer.APROVAR, "Justificativa adequada do parecerista", 800, 1);
        when(registrarParecerUseCase.executar(
                        any(com.dynamis.sep_api.credito.application.dto.RegistrarParecerCommand.class)))
                .thenReturn(parecer);
        when(mapper.toParecerResponse(any())).thenReturn(null);

        RegistrarParecerRequest req =
                new RegistrarParecerRequest(DecisaoParecer.APROVAR, "Justificativa adequada do parecerista");
        mockMvc.perform(post("/api/v1/credito/propostas/{id}/parecer", propostaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void getRegrasComoCliente403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        mockMvc.perform(get("/api/v1/credito/propostas/{id}/regras", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRegrasComoFinanceiro200() throws Exception {
        UUID propostaId = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        when(listarRegrasAvaliadasUseCase.executar(propostaId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/credito/propostas/{id}/regras", propostaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    private static ResultMatcher header(String name, String expectedValue) {
        return MockMvcResultMatchers.header().string(name, expectedValue);
    }
}
