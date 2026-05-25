package com.dynamis.sep_api.cobranca.web.controller;

import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.usecase.AceitarRenegociacaoUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.CalcularValorAtualizadoParcelaUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.ConsultarAgendaPorContratoUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.ConsultarRecebimentosUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.IniciarRenegociacaoUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.ListarInadimplenciaUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.RecusarRenegociacaoUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.RegistrarContatoCobrancaUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.RegistrarRecebimentoUseCase;
import com.dynamis.sep_api.cobranca.domain.exception.CobrancaOwnershipException;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaCobrancaNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoConflitanteException;
import com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.model.EventoCobranca;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.web.mapper.CobrancaWebMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CobrancaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    ApiExceptionHandler.class,
    CobrancaInadimplenciaControllerTest.MethodSecurityTestConfig.class,
    StepUpEnforcementAspect.class
})
class CobrancaInadimplenciaControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    @org.springframework.context.annotation.EnableAspectJAutoProxy
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Mocks de use cases Sprint 12 (precisam estar presentes pro contexto carregar):
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

    // Mocks Task 13.7:
    @MockBean
    private ListarInadimplenciaUseCase listarInadimplenciaUseCase;

    @MockBean
    private RegistrarContatoCobrancaUseCase registrarContatoUseCase;

    @MockBean
    private IniciarRenegociacaoUseCase iniciarRenegociacaoUseCase;

    @MockBean
    private AceitarRenegociacaoUseCase aceitarRenegociacaoUseCase;

    @MockBean
    private RecusarRenegociacaoUseCase recusarRenegociacaoUseCase;

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
        var auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        Usuario u = Usuario.criar("user@sep.test", "hash", role);
        if (mfaHabilitado) {
            u.marcarMfaHabilitado();
        }
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
    }

    // ============== GET /inadimplencia ==============

    @Test
    void listarInadimplencia_financeiro200() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        ParcelaCobranca parcela = parcelaAtrasada();
        when(listarInadimplenciaUseCase.listar(any()))
                .thenReturn(List.of(new ListarInadimplenciaUseCase.LinhaInadimplencia(
                        parcela, UUID.randomUUID(), UUID.randomUUID(), 30)));

        mockMvc.perform(get("/api/v1/cobranca/inadimplencia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].diasAtraso").value(30))
                .andExpect(jsonPath("$[0].status").value("ATRASADA"));
    }

    @Test
    void listarInadimplencia_clienteSemRole_403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);

        mockMvc.perform(get("/api/v1/cobranca/inadimplencia")).andExpect(status().isForbidden());
    }

    @Test
    void listarInadimplencia_semAuth_401() throws Exception {
        mockMvc.perform(get("/api/v1/cobranca/inadimplencia")).andExpect(status().isUnauthorized());
    }

    @Test
    void listarInadimplencia_aplicaFiltros() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        when(listarInadimplenciaUseCase.listar(any())).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/cobranca/inadimplencia")
                        .param("dias_atraso_min", "5")
                        .param("dias_atraso_max", "30")
                        .param("status", "ATRASADA"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<ListarInadimplenciaUseCase.Filtro> captor =
                org.mockito.ArgumentCaptor.forClass(ListarInadimplenciaUseCase.Filtro.class);
        verify(listarInadimplenciaUseCase).listar(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().diasAtrasoMin())
                .isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().diasAtrasoMax())
                .isEqualTo(30);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().status()).containsExactly(StatusParcela.ATRASADA);
    }

    // ============== POST /parcelas/{id}/contato ==============

    @Test
    void registrarContato_financeiro201() throws Exception {
        UUID financeiroId = UUID.randomUUID();
        UUID parcelaId = UUID.randomUUID();
        autenticar(financeiroId, Role.FINANCEIRO);
        EventoCobranca evento =
                EventoCobranca.contatoManual(parcelaId, financeiroId, 30, "Cliente contactado", OffsetDateTime.now());
        when(registrarContatoUseCase.executar(any(), any(), any(), any())).thenReturn(evento);

        String body = "{\"descricao\":\"Cliente contactado\",\"diasAtraso\":30}";
        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/contato", parcelaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("CONTATO_MANUAL"))
                .andExpect(jsonPath("$.descricao").value("Cliente contactado"));
    }

    @Test
    void registrarContato_descricaoVazia_400() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);

        String body = "{\"descricao\":\"\",\"diasAtraso\":30}";
        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/contato", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrarContato_parcelaInexistente_404() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        when(registrarContatoUseCase.executar(any(), any(), any(), any()))
                .thenThrow(ParcelaCobrancaNaoEncontradaException.porId(UUID.randomUUID()));

        String body = "{\"descricao\":\"x\",\"diasAtraso\":1}";
        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/contato", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void registrarContato_cliente_403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        String body = "{\"descricao\":\"x\",\"diasAtraso\":1}";
        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/contato", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ============== POST /parcelas/{id}/renegociacao ==============

    @Test
    void proporRenegociacao_financeiro201() throws Exception {
        UUID financeiroId = UUID.randomUUID();
        UUID parcelaId = UUID.randomUUID();
        autenticar(financeiroId, Role.FINANCEIRO);
        when(iniciarRenegociacaoUseCase.executar(any())).thenReturn(novaRenegociacao(parcelaId));

        String body =
                """
                {"novoValorParcela":110.00,"novoVencimento":"2026-07-10","numeroParcelas":3,"desconto":0.00,"justificativa":"acordo"}
                """;
        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/renegociacao", parcelaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROPOSTA"));
    }

    @Test
    void proporRenegociacao_cliente_403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        String body =
                """
                {"novoValorParcela":110.00,"novoVencimento":"2026-07-10","numeroParcelas":3,"desconto":0.00,"justificativa":"x"}
                """;
        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/renegociacao", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void proporRenegociacao_payloadInvalido_400() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        String body =
                "{\"novoValorParcela\":-5,\"novoVencimento\":\"2026-07-10\",\"numeroParcelas\":0,\"desconto\":-1}";
        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/renegociacao", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verify(iniciarRenegociacaoUseCase, never()).executar(any());
    }

    @Test
    void proporRenegociacao_conflito_409() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        when(iniciarRenegociacaoUseCase.executar(any()))
                .thenThrow(new RenegociacaoConflitanteException(UUID.randomUUID()));
        String body =
                """
                {"novoValorParcela":110.00,"novoVencimento":"2026-07-10","numeroParcelas":3,"desconto":0.00,"justificativa":"x"}
                """;
        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/renegociacao", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // ============== PATCH /renegociacoes/{id}/aceite ==============

    @Test
    void aceitarRenegociacao_owner200() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        UUID renegociacaoId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        when(aceitarRenegociacaoUseCase.executar(any(), any())).thenReturn(novaRenegociacao(UUID.randomUUID()));

        mockMvc.perform(patch("/api/v1/cobranca/renegociacoes/{id}/aceite", renegociacaoId))
                .andExpect(status().isOk());
    }

    @Test
    void aceitarRenegociacao_ownerInvalido_403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        when(aceitarRenegociacaoUseCase.executar(any(), any()))
                .thenThrow(new CobrancaOwnershipException(UUID.randomUUID()));

        mockMvc.perform(patch("/api/v1/cobranca/renegociacoes/{id}/aceite", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aceitarRenegociacao_inexistente_404() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        UUID id = UUID.randomUUID();
        when(aceitarRenegociacaoUseCase.executar(any(), any())).thenThrow(new RenegociacaoNaoEncontradaException(id));

        mockMvc.perform(patch("/api/v1/cobranca/renegociacoes/{id}/aceite", id)).andExpect(status().isNotFound());
    }

    @Test
    void aceitarRenegociacao_jaDecidida_409() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        UUID id = UUID.randomUUID();
        when(aceitarRenegociacaoUseCase.executar(any(), any()))
                .thenThrow(new com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoEstadoInvalidoException(
                        id, com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao.ACEITA, "aceitar"));

        mockMvc.perform(patch("/api/v1/cobranca/renegociacoes/{id}/aceite", id)).andExpect(status().isConflict());
    }

    @Test
    void aceitarRenegociacao_semStepUpComMfaHabilitado_403() throws Exception {
        // Fix review manual Task 13.7: StepUpEnforcementAspect bloqueia quando MFA habilitado
        // e nenhum X-Step-Up-Token enviado.
        autenticar(UUID.randomUUID(), Role.CLIENTE, true);

        mockMvc.perform(patch("/api/v1/cobranca/renegociacoes/{id}/aceite", UUID.randomUUID()))
                .andExpect(status().isForbidden());
        verify(aceitarRenegociacaoUseCase, never()).executar(any(), any());
    }

    @Test
    void proporRenegociacao_semStepUpComMfaHabilitado_403() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO, true);
        String body =
                """
                {"novoValorParcela":110.00,"novoVencimento":"2026-07-10","numeroParcelas":3,"desconto":0.00,"justificativa":"x"}
                """;
        mockMvc.perform(post("/api/v1/cobranca/parcelas/{id}/renegociacao", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        verify(iniciarRenegociacaoUseCase, never()).executar(any());
    }

    // ============== PATCH /renegociacoes/{id}/recusa ==============

    @Test
    void recusarRenegociacao_owner200() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        UUID renegociacaoId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        when(recusarRenegociacaoUseCase.executar(any(), any())).thenReturn(novaRenegociacao(UUID.randomUUID()));

        mockMvc.perform(patch("/api/v1/cobranca/renegociacoes/{id}/recusa", renegociacaoId))
                .andExpect(status().isOk());
    }

    @Test
    void recusarRenegociacao_ownerInvalido_403() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        when(recusarRenegociacaoUseCase.executar(any(), any()))
                .thenThrow(new CobrancaOwnershipException(UUID.randomUUID()));

        mockMvc.perform(patch("/api/v1/cobranca/renegociacoes/{id}/recusa", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    // ============== helpers ==============

    private static ParcelaCobranca parcelaAtrasada() {
        AgendaPagamento agenda = AgendaPagamento.criar(
                UUID.randomUUID(),
                List.of(new ParcelaPlanejada(
                        1, ComposicaoValor.principalApenas(new BigDecimal("100.00")), LocalDate.of(2026, 5, 15))));
        ParcelaCobranca parcela = agenda.getParcelas().get(0);
        parcela.marcarAtrasada();
        return parcela;
    }

    private static Renegociacao novaRenegociacao(UUID parcelaId) {
        OffsetDateTime agora = OffsetDateTime.now();
        return Renegociacao.propor(
                parcelaId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                StatusParcela.ATRASADA,
                new BigDecimal("110.00"),
                LocalDate.of(2026, 7, 10),
                3,
                BigDecimal.ZERO,
                "acordo",
                UUID.randomUUID(),
                agora,
                agora.plusDays(7));
    }
}
