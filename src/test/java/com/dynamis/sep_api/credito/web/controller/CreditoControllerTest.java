package com.dynamis.sep_api.credito.web.controller;

import com.dynamis.sep_api.credito.application.usecase.ConsultarPropostaUseCase;
import com.dynamis.sep_api.credito.application.usecase.CriarPropostaCreditoUseCase;
import com.dynamis.sep_api.credito.application.usecase.ListarPropostasUseCase;
import com.dynamis.sep_api.credito.application.usecase.RegistrarParecerUseCase;
import com.dynamis.sep_api.credito.domain.model.ParecerCredito;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.ParecerCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.RegraCreditoAvaliadaRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ScoreInternoRepository;
import com.dynamis.sep_api.credito.web.dto.CriarPropostaRequest;
import com.dynamis.sep_api.credito.web.dto.RegistrarParecerRequest;
import com.dynamis.sep_api.credito.web.mapper.CreditoWebMapper;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.usuarios.domain.model.Role;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

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
@Import({ApiExceptionHandler.class, CreditoControllerTest.MethodSecurityTestConfig.class})
class CreditoControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CriarPropostaCreditoUseCase criarPropostaUseCase;

    @MockBean
    private ConsultarPropostaUseCase consultarPropostaUseCase;

    @MockBean
    private ListarPropostasUseCase listarPropostasUseCase;

    @MockBean
    private RegistrarParecerUseCase registrarParecerUseCase;

    @MockBean
    private ScoreInternoRepository scoreRepository;

    @MockBean
    private ParecerCreditoRepository parecerRepository;

    @MockBean
    private RegraCreditoAvaliadaRepository regraRepository;

    @MockBean
    private CreditoWebMapper mapper;

    @MockBean
    private PropostaCreditoRepository propostaRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(UUID id, Role role) {
        UsuarioAutenticado p = new UsuarioAutenticado(id, "user@sep.test", role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
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
        when(consultarPropostaUseCase.executar(propostaId)).thenReturn(alheia);

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
        when(consultarPropostaUseCase.executar(propostaId)).thenReturn(qualquer);
        when(scoreRepository.findByPropostaId(any())).thenReturn(Optional.empty());
        when(parecerRepository.findTopByPropostaIdOrderByVersaoDesc(any())).thenReturn(Optional.empty());
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
    void getRegrasComoCliente403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        mockMvc.perform(get("/api/v1/credito/propostas/{id}/regras", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRegrasComoFinanceiro200() throws Exception {
        UUID propostaId = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        PropostaCredito p = PropostaCredito.criar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TipoOperacao.OUTROS,
                new Money(new BigDecimal("10000"), "BRL"),
                12);
        when(consultarPropostaUseCase.executar(propostaId)).thenReturn(p);
        when(regraRepository.findByPropostaIdOrderByDataAvaliacaoAsc(propostaId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/credito/propostas/{id}/regras", propostaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void postParecerFinanceiroRetorna200() throws Exception {
        UUID propostaId = UUID.randomUUID();
        UUID pareceristaId = UUID.randomUUID();
        autenticar(pareceristaId, Role.FINANCEIRO);
        ParecerCredito parecer = ParecerCredito.registrar(
                propostaId, pareceristaId, DecisaoParecer.APROVAR, "Justificativa adequada do parecerista", 800, 1);
        when(registrarParecerUseCase.executar(any(), any(), any(), any())).thenReturn(parecer);
        when(mapper.toParecerResponse(any())).thenReturn(null);

        RegistrarParecerRequest req =
                new RegistrarParecerRequest(DecisaoParecer.APROVAR, "Justificativa adequada do parecerista");
        mockMvc.perform(post("/api/v1/credito/propostas/{id}/parecer", propostaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.ResultMatcher header(String name, String expectedValue) {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(name, expectedValue);
    }
}
