package com.dynamis.sep_api.cobranca.web.controller;

import com.dynamis.sep_api.cobranca.application.dto.RecebimentoTomadorResult;
import com.dynamis.sep_api.cobranca.application.usecase.ConsultarRecebimentosParcelaUseCase;
import com.dynamis.sep_api.cobranca.domain.exception.CobrancaOwnershipException;
import com.dynamis.sep_api.cobranca.web.dto.RecebimentoTomadorResponse;
import com.dynamis.sep_api.cobranca.web.mapper.CobrancaWebMapper;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
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
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CobrancaTomadorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, CobrancaTomadorControllerTest.MethodSecurityTestConfig.class})
class CobrancaTomadorControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConsultarRecebimentosParcelaUseCase consultarRecebimentosParcelaUseCase;

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

    @Test
    void clienteOwner200() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        UUID parcelaId = UUID.randomUUID();
        UUID recebimentoId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        when(consultarRecebimentosParcelaUseCase.executar(eq(parcelaId), eq(tomadorId)))
                .thenReturn(List.of(new RecebimentoTomadorResult(
                        recebimentoId, new BigDecimal("1000.00"), OffsetDateTime.now(), "PIX")));
        when(mapper.toRecebimentoTomadorListResponse(any()))
                .thenReturn(List.of(new RecebimentoTomadorResponse(
                        recebimentoId, new BigDecimal("1000.00"), OffsetDateTime.now(), "PIX")));

        mockMvc.perform(get("/api/v1/cobranca/parcelas/{parcelaId}/recebimentos", parcelaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recebimentoId").value(recebimentoId.toString()))
                .andExpect(jsonPath("$[0].meioPagamento").value("PIX"));
    }

    @Test
    void listaVaziaRetornaArrayVazio() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        when(consultarRecebimentosParcelaUseCase.executar(any(), any())).thenReturn(List.of());
        when(mapper.toRecebimentoTomadorListResponse(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/cobranca/parcelas/{parcelaId}/recebimentos", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void financeiroRejeitado403() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        mockMvc.perform(get("/api/v1/cobranca/parcelas/{parcelaId}/recebimentos", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRejeitado403() throws Exception {
        autenticar(UUID.randomUUID(), Role.ADMIN);
        mockMvc.perform(get("/api/v1/cobranca/parcelas/{parcelaId}/recebimentos", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void parcelaIdInvalido400() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        mockMvc.perform(get("/api/v1/cobranca/parcelas/{parcelaId}/recebimentos", "nao-eh-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownershipFalha403ComMensagemGenericaSemUuid() throws Exception {
        UUID parcelaId = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        when(consultarRecebimentosParcelaUseCase.executar(any(), any())).thenThrow(new CobrancaOwnershipException());

        mockMvc.perform(get("/api/v1/cobranca/parcelas/{parcelaId}/recebimentos", parcelaId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Recurso de cobranca indisponivel"));
    }

    @Test
    void jsonNaoContemCamposProibidos() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        UUID recebimentoId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        when(consultarRecebimentosParcelaUseCase.executar(any(), any()))
                .thenReturn(List.of(new RecebimentoTomadorResult(
                        recebimentoId, new BigDecimal("500.00"), OffsetDateTime.now(), "TRANSFERENCIA")));
        when(mapper.toRecebimentoTomadorListResponse(any()))
                .thenReturn(List.of(new RecebimentoTomadorResponse(
                        recebimentoId, new BigDecimal("500.00"), OffsetDateTime.now(), "TRANSFERENCIA")));

        mockMvc.perform(get("/api/v1/cobranca/parcelas/{parcelaId}/recebimentos", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movimentacaoEscrowId").doesNotExist())
                .andExpect(jsonPath("$[0].identificadorExterno").doesNotExist())
                .andExpect(jsonPath("$[0].novo").doesNotExist())
                .andExpect(jsonPath("$[0].observacao").doesNotExist())
                .andExpect(jsonPath("$[0].registradoPor").doesNotExist())
                .andExpect(jsonPath("$[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$[0].statusParcela").doesNotExist());
    }
}
