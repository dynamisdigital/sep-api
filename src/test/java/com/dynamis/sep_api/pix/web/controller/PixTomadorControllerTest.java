package com.dynamis.sep_api.pix.web.controller;

import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.pix.application.dto.PixDesembolsoTomadorResult;
import com.dynamis.sep_api.pix.application.usecase.ConsultarDesembolsoTomadorUseCase;
import com.dynamis.sep_api.pix.domain.exception.PixLeituraNaoEncontradaException;
import com.dynamis.sep_api.pix.domain.vo.StatusPixPublico;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PixTomadorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, PixTomadorControllerTest.MethodSecurityTestConfig.class})
class PixTomadorControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConsultarDesembolsoTomadorUseCase consultarDesembolsoTomadorUseCase;

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
    void clienteOwner200_semConsumirStepUp() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        UUID contratoId = UUID.randomUUID();
        OffsetDateTime atualizadoEm = OffsetDateTime.now();
        autenticar(tomadorId, Role.CLIENTE);
        when(consultarDesembolsoTomadorUseCase.executar(eq(contratoId), eq(tomadorId)))
                .thenReturn(new PixDesembolsoTomadorResult(
                        StatusPixPublico.EM_PROCESSAMENTO, new BigDecimal("1500.00"), atualizadoEm));

        mockMvc.perform(get("/api/v1/pix/contratos/{contratoId}/desembolso", contratoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_PROCESSAMENTO"))
                .andExpect(jsonPath("$.valor").value(1500.00));

        // GET read-only nao exige nem consome step-up.
        verifyNoInteractions(stepUpTokenService);
    }

    @Test
    void desembolsoInexistenteOuAlheio404() throws Exception {
        UUID contratoId = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        when(consultarDesembolsoTomadorUseCase.executar(any(), any()))
                .thenThrow(new PixLeituraNaoEncontradaException());

        mockMvc.perform(get("/api/v1/pix/contratos/{contratoId}/desembolso", contratoId))
                .andExpect(status().isNotFound());
    }

    @Test
    void contratoIdInvalido400() throws Exception {
        autenticar(UUID.randomUUID(), Role.CLIENTE);
        mockMvc.perform(get("/api/v1/pix/contratos/{contratoId}/desembolso", "nao-eh-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void financeiroRejeitado403() throws Exception {
        autenticar(UUID.randomUUID(), Role.FINANCEIRO);
        mockMvc.perform(get("/api/v1/pix/contratos/{contratoId}/desembolso", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRejeitado403() throws Exception {
        autenticar(UUID.randomUUID(), Role.ADMIN);
        mockMvc.perform(get("/api/v1/pix/contratos/{contratoId}/desembolso", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void backofficeRejeitado403() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE);
        mockMvc.perform(get("/api/v1/pix/contratos/{contratoId}/desembolso", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void jsonNaoContemCamposProibidos() throws Exception {
        UUID tomadorId = UUID.randomUUID();
        autenticar(tomadorId, Role.CLIENTE);
        when(consultarDesembolsoTomadorUseCase.executar(any(), any()))
                .thenReturn(new PixDesembolsoTomadorResult(
                        StatusPixPublico.LIQUIDADO, new BigDecimal("2000.00"), OffsetDateTime.now()));

        mockMvc.perform(get("/api/v1/pix/contratos/{contratoId}/desembolso", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaveDestinoMascara").doesNotExist())
                .andExpect(jsonPath("$.chaveDestinoHash").doesNotExist())
                .andExpect(jsonPath("$.txid").doesNotExist())
                .andExpect(jsonPath("$.externalId").doesNotExist())
                .andExpect(jsonPath("$.correlationId").doesNotExist())
                .andExpect(jsonPath("$.transferenciaId").doesNotExist())
                .andExpect(jsonPath("$.contratoId").doesNotExist())
                .andExpect(jsonPath("$.tomadorId").doesNotExist());
    }
}
