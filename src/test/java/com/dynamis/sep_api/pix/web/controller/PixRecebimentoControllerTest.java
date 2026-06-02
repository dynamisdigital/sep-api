package com.dynamis.sep_api.pix.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.pix.application.dto.GerarReferenciaRecebimentoPixResult;
import com.dynamis.sep_api.pix.application.dto.RecebimentoPixResult;
import com.dynamis.sep_api.pix.application.dto.ReferenciaRecebimentoPixResult;
import com.dynamis.sep_api.pix.application.usecase.ConsultarRecebimentoPixUseCase;
import com.dynamis.sep_api.pix.application.usecase.ConsultarReferenciaRecebimentoPixUseCase;
import com.dynamis.sep_api.pix.application.usecase.GerarReferenciaRecebimentoPixUseCase;
import com.dynamis.sep_api.pix.domain.vo.StatusPixRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Role;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PixRecebimentoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, PixRecebimentoControllerTest.MethodSecurityTestConfig.class})
class PixRecebimentoControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GerarReferenciaRecebimentoPixUseCase gerarReferencia;

    @MockBean
    private ConsultarReferenciaRecebimentoPixUseCase consultarReferencia;

    @MockBean
    private ConsultarRecebimentoPixUseCase consultarRecebimento;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private final UUID parcelaId = UUID.randomUUID();
    private final UUID referenciaId = UUID.randomUUID();
    private final UUID recebimentoId = UUID.randomUUID();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(Role role) {
        UsuarioAutenticado principal = new UsuarioAutenticado(UUID.randomUUID(), "op@sep.test", role);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private GerarReferenciaRecebimentoPixResult gerarResult(boolean novo) {
        return new GerarReferenciaRecebimentoPixResult(
                referenciaId,
                parcelaId,
                "txid-1",
                "00020101...BR",
                new BigDecimal("250.00"),
                StatusPixReferenciaRecebimento.ATIVA,
                novo);
    }

    private String body() {
        return "{\"parcelaId\":\"" + parcelaId + "\"}";
    }

    @Test
    void financeiroGera_201() throws Exception {
        autenticar(Role.FINANCEIRO);
        when(gerarReferencia.executar(any())).thenReturn(gerarResult(true));

        mockMvc.perform(post("/api/v1/pix/recebimentos/referencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.txid").value("txid-1"))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.novo").value(true));
    }

    @Test
    void reapresentacaoIdempotente_200() throws Exception {
        autenticar(Role.ADMIN);
        when(gerarReferencia.executar(any())).thenReturn(gerarResult(false));

        mockMvc.perform(post("/api/v1/pix/recebimentos/referencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.novo").value(false));
    }

    @Test
    void cliente_naoPodeGerar_403() throws Exception {
        autenticar(Role.CLIENTE);

        mockMvc.perform(post("/api/v1/pix/recebimentos/referencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());
    }

    @Test
    void parcelaIdAusente_400() throws Exception {
        autenticar(Role.FINANCEIRO);

        mockMvc.perform(post("/api/v1/pix/recebimentos/referencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void backofficeConsultaReferencia_200() throws Exception {
        autenticar(Role.BACKOFFICE);
        when(consultarReferencia.executar(referenciaId))
                .thenReturn(new ReferenciaRecebimentoPixResult(
                        referenciaId,
                        parcelaId,
                        UUID.randomUUID(),
                        "txid-1",
                        "00020101...BR",
                        new BigDecimal("250.00"),
                        StatusPixReferenciaRecebimento.PAGA));

        mockMvc.perform(get("/api/v1/pix/recebimentos/referencias/{id}", referenciaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGA"));
    }

    @Test
    void backofficeConsultaRecebimento_200() throws Exception {
        autenticar(Role.BACKOFFICE);
        when(consultarRecebimento.executar(recebimentoId))
                .thenReturn(new RecebimentoPixResult(
                        recebimentoId,
                        StatusPixRecebimento.CONCILIADO,
                        new BigDecimal("250.00"),
                        "E2E-1",
                        referenciaId,
                        parcelaId,
                        UUID.randomUUID(),
                        null,
                        OffsetDateTime.now()));

        mockMvc.perform(get("/api/v1/pix/recebimentos/{id}", recebimentoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCILIADO"))
                .andExpect(jsonPath("$.endToEndId").value("E2E-1"));
    }

    @Test
    void cliente_naoConsultaRecebimento_403() throws Exception {
        autenticar(Role.CLIENTE);

        mockMvc.perform(get("/api/v1/pix/recebimentos/{id}", recebimentoId)).andExpect(status().isForbidden());
    }

    @Test
    void recebimentoInexistente_404() throws Exception {
        autenticar(Role.FINANCEIRO);
        when(consultarRecebimento.executar(recebimentoId))
                .thenThrow(new RecursoNaoEncontradoException("PIX-404-RECEBIMENTO", "nao encontrado"));

        mockMvc.perform(get("/api/v1/pix/recebimentos/{id}", recebimentoId)).andExpect(status().isNotFound());
    }
}
