package com.dynamis.sep_api.cobranca.web.controller;

import com.dynamis.sep_api.cobranca.application.dto.ParcelaAtualizadaResult;
import com.dynamis.sep_api.cobranca.application.dto.RegistrarRecebimentoResult;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.usecase.CalcularValorAtualizadoParcelaUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.ConsultarAgendaPorContratoUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.ConsultarRecebimentosUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.RegistrarRecebimentoUseCase;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.web.dto.AgendaPagamentoResponse;
import com.dynamis.sep_api.cobranca.web.dto.RecebimentoResponse;
import com.dynamis.sep_api.cobranca.web.dto.RegistrarRecebimentoRequest;
import com.dynamis.sep_api.cobranca.web.dto.ValorAtualizadoParcelaResponse;
import com.dynamis.sep_api.cobranca.web.mapper.CobrancaWebMapper;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CobrancaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, CobrancaControllerTest.MethodSecurityTestConfig.class})
class CobrancaControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConsultarAgendaPorContratoUseCase consultarAgendaUseCase;

    @MockBean
    private CalcularValorAtualizadoParcelaUseCase calcularValorAtualizadoUseCase;

    @MockBean
    private RegistrarRecebimentoUseCase registrarRecebimentoUseCase;

    @MockBean
    private ConsultarRecebimentosUseCase consultarRecebimentosUseCase;

    @MockBean
    private ContratoCobrancaQueryPort contratoQueryPort;

    @MockBean
    private CobrancaWebMapper mapper;

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
        UsuarioAutenticado p = new UsuarioAutenticado(id, "user@sep.test", role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        Usuario u = Usuario.criar("user@sep.test", "hash", role);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
    }

    private AgendaPagamento novaAgenda(UUID contratoId) {
        return AgendaPagamento.criar(
                contratoId,
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("1000.00")), LocalDate.of(2026, 6, 1))));
    }

    // ============== GET /contratos/{id}/agenda ==============

    @Test
    void getAgendaOwner200() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        UUID contratoId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        when(contratoQueryPort.tomadorIdDoContrato(contratoId)).thenReturn(Optional.of(tomadorId));
        when(consultarAgendaUseCase.executar(contratoId)).thenReturn(novaAgenda(contratoId));
        when(mapper.toAgendaResponse(any()))
                .thenReturn(new AgendaPagamentoResponse(
                        UUID.randomUUID(), contratoId, 1, new BigDecimal("1000.00"), OffsetDateTime.now(), List.of()));

        mockMvc.perform(get("/api/v1/cobranca/contratos/{contratoId}/agenda", contratoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contratoId").value(contratoId.toString()));
    }

    @Test
    void getAgendaClienteAlheio403() throws Exception {
        UUID contratoId = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        when(contratoQueryPort.tomadorIdDoContrato(contratoId)).thenReturn(Optional.of(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/cobranca/contratos/{contratoId}/agenda", contratoId))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAgendaFinanceiroAcessaQualquer200() throws Exception {
        UUID contratoId = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        when(consultarAgendaUseCase.executar(contratoId)).thenReturn(novaAgenda(contratoId));
        when(mapper.toAgendaResponse(any()))
                .thenReturn(new AgendaPagamentoResponse(
                        UUID.randomUUID(), contratoId, 1, new BigDecimal("1000.00"), OffsetDateTime.now(), List.of()));

        mockMvc.perform(get("/api/v1/cobranca/contratos/{contratoId}/agenda", contratoId))
                .andExpect(status().isOk());
    }

    @Test
    void getAgendaUuidInvalido400() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        mockMvc.perform(get("/api/v1/cobranca/contratos/{contratoId}/agenda", "nao-eh-uuid"))
                .andExpect(status().isBadRequest());
    }

    // ============== GET /parcelas/{id} ==============

    @Test
    void getParcelaOwner200() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        UUID contratoId = UUID.randomUUID();
        UUID parcelaId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        when(calcularValorAtualizadoUseCase.resolverContratoId(parcelaId)).thenReturn(Optional.of(contratoId));
        when(contratoQueryPort.tomadorIdDoContrato(contratoId)).thenReturn(Optional.of(tomadorId));
        when(calcularValorAtualizadoUseCase.executar(parcelaId)).thenReturn(novoResult(parcelaId, contratoId));
        when(mapper.toValorAtualizadoResponse(any())).thenReturn(stubValorAtualizado(parcelaId));

        mockMvc.perform(get("/api/v1/cobranca/parcelas/{id}", parcelaId)).andExpect(status().isOk());
    }

    @Test
    void getParcelaClienteAlheio403() throws Exception {
        UUID parcelaId = UUID.randomUUID();
        UUID contratoId = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        when(calcularValorAtualizadoUseCase.resolverContratoId(parcelaId)).thenReturn(Optional.of(contratoId));
        when(contratoQueryPort.tomadorIdDoContrato(contratoId)).thenReturn(Optional.of(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/cobranca/parcelas/{id}", parcelaId)).andExpect(status().isForbidden());
    }

    @Test
    void getParcelaClienteParaParcelaInexistente_unifica403() throws Exception {
        UUID parcelaId = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        when(calcularValorAtualizadoUseCase.resolverContratoId(parcelaId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/cobranca/parcelas/{id}", parcelaId)).andExpect(status().isForbidden());
    }

    // ============== POST /parcelas/{id}/recebimentos ==============

    @Test
    void postRecebimentoClienteRejeitado403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/recebimentos", UUID.randomUUID())
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadRecebimento()))
                .andExpect(status().isForbidden());
    }

    @Test
    void postRecebimentoFinanceiroComIdempotencyKey200() throws Exception {
        UUID parcelaId = UUID.randomUUID();
        UUID operadorId = UUID.randomUUID();
        autenticar(operadorId, Role.FINANCEIRO);
        when(registrarRecebimentoUseCase.executar(any()))
                .thenReturn(new RegistrarRecebimentoResult(
                        UUID.randomUUID(),
                        parcelaId,
                        StatusParcela.PAGA,
                        new BigDecimal("1000.00"),
                        OffsetDateTime.now(),
                        "TRANSFERENCIA",
                        "comp-1",
                        UUID.randomUUID(),
                        true));
        when(mapper.toRecebimentoResponse(any(RegistrarRecebimentoResult.class)))
                .thenReturn(new RecebimentoResponse(
                        UUID.randomUUID(),
                        parcelaId,
                        StatusParcela.PAGA,
                        new BigDecimal("1000.00"),
                        OffsetDateTime.now(),
                        "TRANSFERENCIA",
                        "comp-1",
                        UUID.randomUUID(),
                        true));

        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/recebimentos", parcelaId)
                        .header("Idempotency-Key", "k-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadRecebimento()))
                .andExpect(status().isOk());
    }

    @Test
    void postRecebimentoSemIdempotencyKey400() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/recebimentos", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadRecebimento()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postRecebimentoIdempotencyKeyComEspacos400() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/recebimentos", UUID.randomUUID())
                        .header("Idempotency-Key", "tem espaco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadRecebimento()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postRecebimentoPayloadInvalido400() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        // valor zero falha @DecimalMin(0.01)
        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/recebimentos", UUID.randomUUID())
                        .header("Idempotency-Key", "k-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valorRecebido\":0,\"dataRecebimento\":\"2026-05-22T10:00:00-03:00\","
                                + "\"meioPagamento\":\"TRANSFERENCIA\"}"))
                .andExpect(status().isBadRequest());
    }

    // ============== GET /recebimentos ==============

    @Test
    void getRecebimentosClienteRejeitado403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        mockMvc.perform(get("/api/v1/cobranca/recebimentos")).andExpect(status().isForbidden());
    }

    @Test
    void getRecebimentosFinanceiro200() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        when(consultarRecebimentosUseCase.listar()).thenReturn(List.of());
        when(mapper.toRecebimentoListResponse(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/cobranca/recebimentos")).andExpect(status().isOk());
    }

    private String payloadRecebimento() throws Exception {
        return objectMapper.writeValueAsString(new RegistrarRecebimentoRequest(
                new BigDecimal("1000.00"), OffsetDateTime.now(), "TRANSFERENCIA", "comp-1", null));
    }

    private static ParcelaAtualizadaResult novoResult(UUID parcelaId, UUID contratoId) {
        return new ParcelaAtualizadaResult(
                parcelaId,
                contratoId,
                1,
                StatusParcela.PENDENTE,
                LocalDate.of(2026, 6, 1),
                ComposicaoValor.principalApenas(new BigDecimal("1000.00")),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                new BigDecimal("1000.00"));
    }

    private static ValorAtualizadoParcelaResponse stubValorAtualizado(UUID parcelaId) {
        return new ValorAtualizadoParcelaResponse(
                parcelaId,
                1,
                StatusParcela.PENDENTE,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                new BigDecimal("1000.00"));
    }
}
