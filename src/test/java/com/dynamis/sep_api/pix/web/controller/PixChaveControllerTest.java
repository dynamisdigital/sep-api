package com.dynamis.sep_api.pix.web.controller;

import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.StepUpEnforcementAspect;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.pix.application.dto.CadastrarChavePixResult;
import com.dynamis.sep_api.pix.application.dto.ChavePixItemResult;
import com.dynamis.sep_api.pix.application.usecase.CadastrarChavePixUseCase;
import com.dynamis.sep_api.pix.application.usecase.ListarChavesPixUseCase;
import com.dynamis.sep_api.pix.application.usecase.RemoverChavePixUseCase;
import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
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
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PixChaveController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, PixChaveControllerTest.MethodSecurityTestConfig.class, StepUpEnforcementAspect.class
})
class PixChaveControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    @EnableAspectJAutoProxy
    static class MethodSecurityTestConfig {}

    private static final String VALOR = "usuario@empresa.com";
    private static final OffsetDateTime CRIADA_EM = OffsetDateTime.of(2026, 7, 14, 10, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CadastrarChavePixUseCase cadastrarChave;

    @MockBean
    private ListarChavesPixUseCase listarChaves;

    @MockBean
    private RemoverChavePixUseCase removerChave;

    @MockBean
    private StepUpTokenService stepUpTokenService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private final UUID operadorId = UUID.randomUUID();
    private final UUID chaveId = UUID.randomUUID();

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
        return "{\"tipo\":\"EMAIL\",\"valor\":\"" + VALOR + "\"}";
    }

    private CadastrarChavePixResult result(boolean novo) {
        return new CadastrarChavePixResult(
                chaveId, TipoChavePix.EMAIL, "us***************om", StatusChavePix.ATIVA, CRIADA_EM, null, novo);
    }

    @Test
    void financeiroComMfaEStepUp_cadastra201SemCamposProibidos() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(cadastrarChave.executar(any())).thenReturn(result(true));

        mockMvc.perform(post("/api/v1/pix/chaves")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorMascarado").value("us***************om"))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.novo").doesNotExist())
                .andExpect(jsonPath("$.valorHash").doesNotExist())
                .andExpect(jsonPath("$.providerKeyId").doesNotExist())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist())
                .andExpect(content().string(not(containsString(VALOR))));
    }

    @Test
    void replayIdempotente_200() throws Exception {
        autenticar(Role.ADMIN);
        mfaHabilitado(true);
        stepUpValido();
        when(cadastrarChave.executar(any())).thenReturn(result(false));

        mockMvc.perform(post("/api/v1/pix/chaves")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isOk());
    }

    @Test
    void postSemStepUpToken_403() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);

        mockMvc.perform(post("/api/v1/pix/chaves")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());
    }

    @Test
    void postSemMfa_403_estritoSemBypass() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(false);

        mockMvc.perform(post("/api/v1/pix/chaves")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteEmQualquerEndpoint_403() throws Exception {
        autenticar(Role.CLIENTE);

        mockMvc.perform(post("/api/v1/pix/chaves")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/pix/chaves")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/pix/chaves/{id}", chaveId).header("X-Step-Up-Token", "tok-ok"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postSemIdempotencyKey_400() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(cadastrarChave.executar(any()))
                .thenThrow(new ValidacaoException("PIX-400-IDEMPOTENCY-KEY", "Idempotency-Key obrigatoria."));

        mockMvc.perform(post("/api/v1/pix/chaves")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void valorInvalido_400SemEcoarValor() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(cadastrarChave.executar(any()))
                .thenThrow(new ValidacaoException("PIX-400-CHAVE", "chave Pix invalida para o tipo EMAIL."));

        mockMvc.perform(post("/api/v1/pix/chaves")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"EMAIL\",\"valor\":\"chave-invalida@x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("chave-invalida@x"))));
    }

    @Test
    void corpoSemValor_400PelaBeanValidation() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();

        mockMvc.perform(post("/api/v1/pix/chaves")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"EMAIL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listarFinanceiroSemStepUp_200ComArrayMascarado() throws Exception {
        autenticar(Role.FINANCEIRO);
        when(listarChaves.executar())
                .thenReturn(List.of(new ChavePixItemResult(
                        chaveId,
                        TipoChavePix.EMAIL,
                        "us***************om",
                        StatusChavePix.INATIVA,
                        CRIADA_EM,
                        CRIADA_EM.plusDays(1))));

        mockMvc.perform(get("/api/v1/pix/chaves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].valorMascarado").value("us***************om"))
                .andExpect(jsonPath("$[0].status").value("INATIVA"))
                .andExpect(jsonPath("$[0].valorHash").doesNotExist())
                .andExpect(jsonPath("$[0].providerKeyId").doesNotExist())
                .andExpect(content().string(not(containsString(VALOR))));
    }

    @Test
    void listarVazio_200ComArrayVazio() throws Exception {
        autenticar(Role.ADMIN);
        when(listarChaves.executar()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/pix/chaves"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void deleteComStepUp_204() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();

        mockMvc.perform(delete("/api/v1/pix/chaves/{id}", chaveId).header("X-Step-Up-Token", "tok-ok"))
                .andExpect(status().isNoContent());

        verify(removerChave).executar(eq(chaveId), eq(operadorId), any());
    }

    @Test
    void deleteSemStepUp_403() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);

        mockMvc.perform(delete("/api/v1/pix/chaves/{id}", chaveId)).andExpect(status().isForbidden());
    }

    @Test
    void deleteInexistente_404NeutroSemUuid() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        doThrow(new RecursoNaoEncontradoException("PIX-404-CHAVE", "Chave Pix nao encontrada."))
                .when(removerChave)
                .executar(eq(chaveId), eq(operadorId), any());

        // O campo `path` do ErrorResponseDto ecoa a URI que o proprio cliente enviou (nao e
        // vazamento); a neutralidade exigida e da mensagem, que nao pode citar o UUID.
        mockMvc.perform(delete("/api/v1/pix/chaves/{id}", chaveId).header("X-Step-Up-Token", "tok-ok"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", not(containsString(chaveId.toString()))));
    }
}
