package com.dynamis.sep_api.pix.web.controller;

import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.StepUpEnforcementAspect;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.pix.application.dto.SolicitarDesembolsoPixResult;
import com.dynamis.sep_api.pix.application.dto.StatusDesembolsoPixResult;
import com.dynamis.sep_api.pix.application.usecase.ConsultarStatusDesembolsoPixUseCase;
import com.dynamis.sep_api.pix.application.usecase.SolicitarDesembolsoPixUseCase;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
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

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PixDesembolsoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    ApiExceptionHandler.class,
    PixDesembolsoControllerTest.MethodSecurityTestConfig.class,
    StepUpEnforcementAspect.class
})
class PixDesembolsoControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    @EnableAspectJAutoProxy
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SolicitarDesembolsoPixUseCase solicitarDesembolso;

    @MockBean
    private ConsultarStatusDesembolsoPixUseCase consultarStatus;

    @MockBean
    private StepUpTokenService stepUpTokenService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private final UUID operadorId = UUID.randomUUID();
    private final UUID contratoId = UUID.randomUUID();
    private final UUID transferenciaId = UUID.randomUUID();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(Role role) {
        UsuarioAutenticado principal = new UsuarioAutenticado(operadorId, "op@sep.test", role);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void mfaHabilitado(boolean habilitado) {
        Usuario usuario = org.mockito.Mockito.mock(Usuario.class);
        when(usuario.isMfaHabilitado()).thenReturn(habilitado);
        when(usuarioRepository.findById(operadorId)).thenReturn(Optional.of(usuario));
    }

    private void stepUpValido() {
        when(stepUpTokenService.validarEConsumir("tok-ok")).thenReturn(Optional.of(operadorId));
    }

    private String body() {
        return "{\"contratoId\":\"" + contratoId + "\",\"valor\":10000.00,\"chavePixDestino\":\"op@empresa.com\"}";
    }

    private SolicitarDesembolsoPixResult result(boolean novo) {
        return new SolicitarDesembolsoPixResult(
                transferenciaId,
                contratoId,
                StatusPixTransferencia.SOLICITADA,
                new BigDecimal("10000.00"),
                "op****om",
                novo);
    }

    @Test
    void financeiroComMfaEStepUp_solicita201SemChaveEmClaro() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(solicitarDesembolso.executar(any())).thenReturn(result(true));

        mockMvc.perform(post("/api/v1/pix/desembolsos")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chaveDestinoMascara").value("op****om"))
                .andExpect(jsonPath("$.status").value("SOLICITADA"))
                .andExpect(jsonPath(
                        "$.chaveDestinoMascara",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("empresa"))));
    }

    @Test
    void replayIdempotente_200() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(solicitarDesembolso.executar(any())).thenReturn(result(false));

        mockMvc.perform(post("/api/v1/pix/desembolsos")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.novo").value(false));
    }

    @Test
    void financeiroSemStepUpToken_403() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);

        mockMvc.perform(post("/api/v1/pix/desembolsos")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeiroSemMfa_403_strictSemBypass() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(false);

        mockMvc.perform(post("/api/v1/pix/desembolsos")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cliente_403() throws Exception {
        autenticar(Role.CLIENTE);

        mockMvc.perform(post("/api/v1/pix/desembolsos")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());
    }

    @Test
    void valorInvalido_400() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();

        String corpo = "{\"contratoId\":\"" + contratoId + "\",\"valor\":-5,\"chavePixDestino\":\"op@empresa.com\"}";
        mockMvc.perform(post("/api/v1/pix/desembolsos")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semIdempotencyKey_400() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(solicitarDesembolso.executar(any()))
                .thenThrow(new ValidacaoException("PIX-400-IDEMPOTENCY-KEY", "Idempotency-Key obrigatoria."));

        mockMvc.perform(post("/api/v1/pix/desembolsos")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consultarStatus_200() throws Exception {
        autenticar(Role.BACKOFFICE);
        when(consultarStatus.executar(any()))
                .thenReturn(new StatusDesembolsoPixResult(
                        transferenciaId,
                        contratoId,
                        StatusPixTransferencia.CONCLUIDA,
                        new BigDecimal("10000.00"),
                        "op****om",
                        false));

        mockMvc.perform(get("/api/v1/pix/desembolsos/{id}", transferenciaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
    }
}
