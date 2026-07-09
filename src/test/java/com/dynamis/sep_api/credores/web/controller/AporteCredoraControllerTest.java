package com.dynamis.sep_api.credores.web.controller;

import com.dynamis.sep_api.credores.application.dto.AporteCredoraView;
import com.dynamis.sep_api.credores.application.dto.RegistrarAporteCredoraCommand;
import com.dynamis.sep_api.credores.application.dto.RegistrarAporteCredoraResult;
import com.dynamis.sep_api.credores.application.usecase.RegistrarAporteCredoraUseCase;
import com.dynamis.sep_api.credores.domain.exception.AporteConflitanteException;
import com.dynamis.sep_api.credores.domain.exception.AporteNaoElegivelException;
import com.dynamis.sep_api.credores.domain.exception.AporteOperacaoNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.vo.StatusAporteCredora;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.StepUpEnforcementAspect;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Borda web do registro assistido de aporte (Sprint 29 Task 29.4): roles, step-up estrito sem
 * bypass, Idempotency-Key obrigatoria, semantica 201/200 e DTO minimo sem campos internos.
 */
@WebMvcTest(controllers = AporteCredoraController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    ApiExceptionHandler.class,
    AporteCredoraControllerTest.MethodSecurityTestConfig.class,
    StepUpEnforcementAspect.class
})
class AporteCredoraControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    @EnableAspectJAutoProxy
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegistrarAporteCredoraUseCase registrarUseCase;

    @MockBean
    private StepUpTokenService stepUpTokenService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private final UUID operadorId = UUID.randomUUID();
    private final UUID operacaoId = UUID.randomUUID();
    private final UUID aporteId = UUID.randomUUID();

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

    private RegistrarAporteCredoraResult resultado(boolean novo) {
        return new RegistrarAporteCredoraResult(
                new AporteCredoraView(
                        aporteId,
                        operacaoId,
                        StatusAporteCredora.EM_PROCESSAMENTO,
                        new BigDecimal("2500.00"),
                        OffsetDateTime.now(),
                        OffsetDateTime.now()),
                novo);
    }

    @Test
    void financeiroComStepUp_registra201SemCamposInternos() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(registrarUseCase.executar(any())).thenReturn(resultado(true));

        mockMvc.perform(post("/api/v1/credores/operacoes/{operacaoId}/aportes", operacaoId)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":2500.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(aporteId.toString()))
                .andExpect(jsonPath("$.operacaoId").value(operacaoId.toString()))
                .andExpect(jsonPath("$.status").value("EM_PROCESSAMENTO"))
                .andExpect(jsonPath("$.valor").value(2500.00))
                .andExpect(jsonPath("$.dataCriacao").exists())
                .andExpect(jsonPath("$.dataAtualizacao").exists())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.referenciaEscrow").doesNotExist())
                .andExpect(jsonPath("$.motivoFalhaSanitizado").doesNotExist());

        ArgumentCaptor<RegistrarAporteCredoraCommand> captor =
                ArgumentCaptor.forClass(RegistrarAporteCredoraCommand.class);
        verify(registrarUseCase).executar(captor.capture());
        assertThat(captor.getValue().operacaoId()).isEqualTo(operacaoId);
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("idem-1");
        assertThat(captor.getValue().atorId()).isEqualTo(operadorId);
    }

    @Test
    void adminComStepUp_registra201() throws Exception {
        autenticar(Role.ADMIN);
        mfaHabilitado(true);
        stepUpValido();
        when(registrarUseCase.executar(any())).thenReturn(resultado(true));

        mockMvc.perform(post("/api/v1/credores/operacoes/{operacaoId}/aportes", operacaoId)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":2500.00}"))
                .andExpect(status().isCreated());
    }

    @Test
    void replayIdempotente_200() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(registrarUseCase.executar(any())).thenReturn(resultado(false));

        mockMvc.perform(post("/api/v1/credores/operacoes/{operacaoId}/aportes", operacaoId)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":2500.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aporteId.toString()));
    }

    @Test
    void clienteSemPermissao_403() throws Exception {
        autenticar(Role.CLIENTE);

        mockMvc.perform(post("/api/v1/credores/operacoes/{operacaoId}/aportes", operacaoId)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":2500.00}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(registrarUseCase);
    }

    @Test
    void financeiroSemStepUpToken_403() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);

        mockMvc.perform(post("/api/v1/credores/operacoes/{operacaoId}/aportes", operacaoId)
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":2500.00}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(registrarUseCase);
    }

    @Test
    void financeiroSemMfa_403_estritoSemBypass() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(false);
        stepUpValido();

        mockMvc.perform(post("/api/v1/credores/operacoes/{operacaoId}/aportes", operacaoId)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":2500.00}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(registrarUseCase);
    }

    @Test
    void semIdempotencyKey_400() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(registrarUseCase.executar(any()))
                .thenThrow(new ValidacaoException("CRD-400-003", "Idempotency-Key obrigatoria."));

        mockMvc.perform(post("/api/v1/credores/operacoes/{operacaoId}/aportes", operacaoId)
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":2500.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void valorInvalidoNoCorpo_400() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();

        mockMvc.perform(post("/api/v1/credores/operacoes/{operacaoId}/aportes", operacaoId)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":-5}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrarUseCase);
    }

    @Test
    void operacaoInexistente_404NeutroSemUuid() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(registrarUseCase.executar(any())).thenThrow(new AporteOperacaoNaoEncontradaException());

        mockMvc.perform(post("/api/v1/credores/operacoes/{operacaoId}/aportes", operacaoId)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":2500.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(
                        "$.message",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(operacaoId.toString()))));
    }

    @Test
    void contratoNaoAssinado_409() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(registrarUseCase.executar(any())).thenThrow(new AporteNaoElegivelException());

        mockMvc.perform(post("/api/v1/credores/operacoes/{operacaoId}/aportes", operacaoId)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":2500.00}"))
                .andExpect(status().isConflict());
    }

    @Test
    void idempotencyKeyConflitante_409() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(registrarUseCase.executar(any())).thenThrow(new AporteConflitanteException());

        mockMvc.perform(post("/api/v1/credores/operacoes/{operacaoId}/aportes", operacaoId)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":999.99}"))
                .andExpect(status().isConflict());
    }
}
