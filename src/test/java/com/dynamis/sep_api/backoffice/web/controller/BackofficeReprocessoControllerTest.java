package com.dynamis.sep_api.backoffice.web.controller;

import com.dynamis.sep_api.backoffice.application.usecase.ReprocessarChamadaProviderUseCase;
import com.dynamis.sep_api.backoffice.application.usecase.ReprocessarWebhookUseCase;
import com.dynamis.sep_api.backoffice.domain.exception.LimiteReprocessoExcedidoException;
import com.dynamis.sep_api.backoffice.domain.model.Reprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.StepUpEnforcementAspect;
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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BackofficeReprocessoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    ApiExceptionHandler.class,
    BackofficeReprocessoControllerTest.MethodSecurityTestConfig.class,
    StepUpEnforcementAspect.class
})
class BackofficeReprocessoControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    @org.springframework.context.annotation.EnableAspectJAutoProxy
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReprocessarWebhookUseCase reprocessarWebhook;

    @MockBean
    private ReprocessarChamadaProviderUseCase reprocessarProvider;

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

    private void autenticar(UUID id, Role role, boolean mfaHabilitado) {
        UsuarioAutenticado p = new UsuarioAutenticado(id, "op@sep.test", role);
        var auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        Usuario u = Usuario.criar("op@sep.test", "hash", role);
        if (mfaHabilitado) u.marcarMfaHabilitado();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
    }

    @Test
    void reprocessarWebhook_happy201() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);
        Reprocesso r = Reprocesso.paraWebhook(null, UUID.randomUUID(), OffsetDateTime.now(), UUID.randomUUID());
        r.concluirComSucesso("ok");
        when(reprocessarWebhook.executar(any(), any(), any())).thenReturn(r);

        mockMvc.perform(post("/api/v1/backoffice/reprocessos/webhook/{id}", UUID.randomUUID()))
                .andExpect(status().isCreated());
    }

    @Test
    void reprocessarWebhook_limiteExcedido_429() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);
        when(reprocessarWebhook.executar(any(), any(), any())).thenThrow(new LimiteReprocessoExcedidoException("X"));

        mockMvc.perform(post("/api/v1/backoffice/reprocessos/webhook/{id}", UUID.randomUUID()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void reprocessarWebhook_semStepUpMfa_403() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, true);

        mockMvc.perform(post("/api/v1/backoffice/reprocessos/webhook/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
        verify(reprocessarWebhook, never()).executar(any(), any(), any());
    }

    @Test
    void reprocessarProvider_happy201() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);
        Reprocesso r = Reprocesso.paraProvider(
                null, TipoChamadaProvider.KYC, UUID.randomUUID(), OffsetDateTime.now(), UUID.randomUUID());
        r.concluirComSucesso("ok");
        when(reprocessarProvider.executar(any(), any(), any(), any())).thenReturn(r);

        mockMvc.perform(post("/api/v1/backoffice/reprocessos/provider/{tipo}/{id}", "KYC", UUID.randomUUID()))
                .andExpect(status().isCreated());
    }

    @Test
    void reprocessarProvider_tipoInvalido_400() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);

        mockMvc.perform(post("/api/v1/backoffice/reprocessos/provider/{tipo}/{id}", "NAO_EXISTE", UUID.randomUUID()))
                .andExpect(status().isBadRequest());
        verify(reprocessarProvider, never()).executar(any(), any(), any(), any());
    }

    @Test
    void reprocessarProvider_dispatcherSemStrategy_400() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);
        when(reprocessarProvider.executar(any(), any(), any(), any()))
                .thenThrow(new com.dynamis.sep_api.backoffice.domain.exception.TipoReprocessoNaoSuportadoException(
                        com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider.KYC));

        mockMvc.perform(post("/api/v1/backoffice/reprocessos/provider/{tipo}/{id}", "KYC", UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }
}
