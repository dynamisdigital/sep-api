package com.dynamis.sep_api.credito.web.controller;

import com.dynamis.sep_api.credito.application.usecase.IniciarConsentimentoOpenFinanceUseCase;
import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.MovimentacaoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.ConsentimentoOpenFinanceRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.MovimentacaoOpenFinanceRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.web.dto.IniciarConsentimentoOpenFinanceRequest;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OpenFinanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, OpenFinanceControllerTest.MethodSecurityTestConfig.class})
class OpenFinanceControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IniciarConsentimentoOpenFinanceUseCase iniciarUseCase;

    @MockBean
    private PropostaCreditoRepository propostaRepository;

    @MockBean
    private ConsentimentoOpenFinanceRepository consentimentoRepository;

    @MockBean
    private MovimentacaoOpenFinanceRepository movimentacaoRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(UUID id, Role role) {
        UsuarioAutenticado p = new UsuarioAutenticado(id, "user@sep.test", role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(Usuario.criar("user@sep.test", "hash", role)));
    }

    @Test
    void postConsentimentoSemAutenticacao401() throws Exception {
        IniciarConsentimentoOpenFinanceRequest req =
                new IniciarConsentimentoOpenFinanceRequest("52998224725", "https://sep/cb");
        mockMvc.perform(post("/api/v1/credito/propostas/{id}/open-finance/consentimento", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postConsentimentoComoAdmin403() throws Exception {
        autenticar(UUID.randomUUID(), Role.ADMIN);
        IniciarConsentimentoOpenFinanceRequest req =
                new IniciarConsentimentoOpenFinanceRequest("52998224725", "https://sep/cb");
        mockMvc.perform(post("/api/v1/credito/propostas/{id}/open-finance/consentimento", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postConsentimentoClienteRetorna201() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        ConsentimentoOpenFinance c = ConsentimentoOpenFinance.iniciar(
                propostaId,
                tomadorId,
                "https://celcoin/auth/abc",
                "ext-celcoin-1",
                OffsetDateTime.now().plusDays(30));
        when(iniciarUseCase.executar(any())).thenReturn(c);

        IniciarConsentimentoOpenFinanceRequest req =
                new IniciarConsentimentoOpenFinanceRequest("52998224725", "https://sep/cb");
        mockMvc.perform(post("/api/v1/credito/propostas/{id}/open-finance/consentimento", propostaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.urlAutorizacao").value("https://celcoin/auth/abc"));
    }

    @Test
    void postConsentimentoBodyInvalido400() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        mockMvc.perform(post("/api/v1/credito/propostas/{id}/open-finance/consentimento", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpfCnpjTomador\":\"\",\"redirectUri\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postConsentimentoRedirectUriMaliciosoRejeitado400() throws Exception {
        // Sprint 9 fix code review Task 9.6: bloqueio SSRF/open redirect — scheme nao-http.
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        mockMvc.perform(post("/api/v1/credito/propostas/{id}/open-finance/consentimento", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpfCnpjTomador\":\"52998224725\",\"redirectUri\":\"javascript://evil.com/x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postConsentimentoCpfCnpjFormatoInvalido400() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        mockMvc.perform(post("/api/v1/credito/propostas/{id}/open-finance/consentimento", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpfCnpjTomador\":\"123\",\"redirectUri\":\"https://sep/cb\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStatusClienteDono200() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        PropostaCredito p = PropostaCredito.criar(
                tomadorId, UUID.randomUUID(), TipoOperacao.OUTROS, new Money(new BigDecimal("10000"), "BRL"), 12);
        ConsentimentoOpenFinance c = ConsentimentoOpenFinance.iniciar(
                propostaId, tomadorId, "u", "ext-1", OffsetDateTime.now().plusDays(30));
        c.autorizar();
        MovimentacaoOpenFinance mov = MovimentacaoOpenFinance.registrar(
                c.getId(),
                propostaId,
                "{}",
                new BigDecimal("10000"),
                new BigDecimal("7000"),
                new BigDecimal("3000"),
                6);
        when(propostaRepository.findById(propostaId)).thenReturn(Optional.of(p));
        when(consentimentoRepository.findFirstByPropostaIdOrderByDataInicioDesc(propostaId))
                .thenReturn(Optional.of(c));
        when(movimentacaoRepository.findByConsentimentoId(c.getId())).thenReturn(Optional.of(mov));

        mockMvc.perform(get("/api/v1/credito/propostas/{id}/open-finance", propostaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusConsentimento").value("AUTORIZADO"))
                .andExpect(jsonPath("$.ultimaMovimentacao.numeroMesesAvaliados").value(6));
    }

    @Test
    void getStatusClienteAlheio403() throws Exception {
        UUID propostaId = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        PropostaCredito p = PropostaCredito.criar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TipoOperacao.OUTROS,
                new Money(new BigDecimal("10000"), "BRL"),
                12);
        when(propostaRepository.findById(propostaId)).thenReturn(Optional.of(p));

        mockMvc.perform(get("/api/v1/credito/propostas/{id}/open-finance", propostaId))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStatusFinanceiroVeQualquer200() throws Exception {
        UUID propostaId = UUID.randomUUID();
        UUID tomadorAlheio = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        PropostaCredito p = PropostaCredito.criar(
                tomadorAlheio, UUID.randomUUID(), TipoOperacao.OUTROS, new Money(new BigDecimal("10000"), "BRL"), 12);
        ConsentimentoOpenFinance c = ConsentimentoOpenFinance.iniciar(
                propostaId, tomadorAlheio, "u", "ext-1", OffsetDateTime.now().plusDays(30));
        when(propostaRepository.findById(propostaId)).thenReturn(Optional.of(p));
        when(consentimentoRepository.findFirstByPropostaIdOrderByDataInicioDesc(propostaId))
                .thenReturn(Optional.of(c));
        when(movimentacaoRepository.findByConsentimentoId(c.getId())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/credito/propostas/{id}/open-finance", propostaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusConsentimento").value("PENDENTE"));
    }

    @Test
    void getStatusPropostaInexistente404() throws Exception {
        UUID propostaId = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        when(propostaRepository.findById(propostaId)).thenThrow(new PropostaNaoEncontradaException(propostaId));

        mockMvc.perform(get("/api/v1/credito/propostas/{id}/open-finance", propostaId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStatusSemConsentimento404() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        UUID propostaId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        PropostaCredito p = PropostaCredito.criar(
                tomadorId, UUID.randomUUID(), TipoOperacao.OUTROS, new Money(new BigDecimal("10000"), "BRL"), 12);
        when(propostaRepository.findById(propostaId)).thenReturn(Optional.of(p));
        when(consentimentoRepository.findFirstByPropostaIdOrderByDataInicioDesc(propostaId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/credito/propostas/{id}/open-finance", propostaId))
                .andExpect(status().isNotFound());
    }
}
