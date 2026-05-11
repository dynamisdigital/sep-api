package com.dynamis.sep_api.identity.web.controller;

import com.dynamis.sep_api.identity.application.exception.MfaJaHabilitadoException;
import com.dynamis.sep_api.identity.application.exception.TotpInvalidoException;
import com.dynamis.sep_api.identity.application.usecase.ConfirmarTotpUseCase;
import com.dynamis.sep_api.identity.application.usecase.DesabilitarTotpUseCase;
import com.dynamis.sep_api.identity.application.usecase.HabilitarTotpUseCase;
import com.dynamis.sep_api.identity.application.usecase.VerificarTotpUseCase;
import com.dynamis.sep_api.identity.infrastructure.security.ApiAccessDeniedHandler;
import com.dynamis.sep_api.identity.infrastructure.security.ApiAuthenticationEntryPoint;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.identity.web.dto.TotpConfirmRequestDto;
import com.dynamis.sep_api.identity.web.dto.TotpDisableRequestDto;
import com.dynamis.sep_api.identity.web.dto.TotpSetupResponseDto;
import com.dynamis.sep_api.identity.web.dto.TotpVerifyRequestDto;
import com.dynamis.sep_api.identity.web.dto.TotpVerifyResponseDto;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MfaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class MfaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HabilitarTotpUseCase habilitar;

    @MockBean
    private ConfirmarTotpUseCase confirmar;

    @MockBean
    private VerificarTotpUseCase verificar;

    @MockBean
    private DesabilitarTotpUseCase desabilitar;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

    @MockBean
    private ApiAccessDeniedHandler apiAccessDeniedHandler;

    private void autenticar(UUID id) {
        UsuarioAutenticado principal = new UsuarioAutenticado(id, "u@sep.test", Role.CLIENTE);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setupRetorna200ComSetupResponse() throws Exception {
        UUID id = UUID.randomUUID();
        autenticar(id);
        when(habilitar.executar(id))
                .thenReturn(new TotpSetupResponseDto(
                        "SECRET", "otpauth://uri", "data:image/png;base64,X", List.of("AAAA1111")));

        mockMvc.perform(post("/api/v1/auth/totp/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secretBase32").value("SECRET"))
                .andExpect(jsonPath("$.qrCodeDataUrl").value("data:image/png;base64,X"))
                .andExpect(jsonPath("$.backupCodes[0]").value("AAAA1111"));
    }

    @Test
    void setupComMfaJaAtivoRetorna409() throws Exception {
        UUID id = UUID.randomUUID();
        autenticar(id);
        when(habilitar.executar(id)).thenThrow(new MfaJaHabilitadoException());

        mockMvc.perform(post("/api/v1/auth/totp/setup")).andExpect(status().isConflict());
    }

    @Test
    void confirmRetorna204QuandoCodigoValido() throws Exception {
        UUID id = UUID.randomUUID();
        autenticar(id);
        doNothing().when(confirmar).executar(eq(id), eq("123456"));

        mockMvc.perform(post("/api/v1/auth/totp/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpConfirmRequestDto("123456"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void confirmComCodigoErradoRetorna400() throws Exception {
        UUID id = UUID.randomUUID();
        autenticar(id);
        doThrow(new TotpInvalidoException()).when(confirmar).executar(eq(id), eq("999999"));

        mockMvc.perform(post("/api/v1/auth/totp/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpConfirmRequestDto("999999"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmComBodyInvalidoRetorna400() throws Exception {
        autenticar(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/auth/totp/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpConfirmRequestDto("abc"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyAceitaCodigoTotp() throws Exception {
        UUID id = UUID.randomUUID();
        when(verificar.executar(eq(id), eq("123456"))).thenReturn(new TotpVerifyResponseDto(true, false));

        mockMvc.perform(post("/api/v1/auth/totp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpVerifyRequestDto(id, "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificado").value(true))
                .andExpect(jsonPath("$.usouBackupCode").value(false));
    }

    @Test
    void verifyAceitaBackupCode() throws Exception {
        UUID id = UUID.randomUUID();
        when(verificar.executar(eq(id), eq("AAAA1111"))).thenReturn(new TotpVerifyResponseDto(true, true));

        mockMvc.perform(post("/api/v1/auth/totp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpVerifyRequestDto(id, "AAAA1111"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usouBackupCode").value(true));
    }

    @Test
    void verifyCodigoInvalidoRetorna400() throws Exception {
        UUID id = UUID.randomUUID();
        when(verificar.executar(any(), any())).thenThrow(new TotpInvalidoException());

        mockMvc.perform(post("/api/v1/auth/totp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpVerifyRequestDto(id, "999999"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disableRetorna204ComSenhaCorreta() throws Exception {
        UUID id = UUID.randomUUID();
        autenticar(id);
        doNothing().when(desabilitar).executar(eq(id), eq("senha-ok"));

        mockMvc.perform(post("/api/v1/auth/totp/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpDisableRequestDto("senha-ok"))))
                .andExpect(status().isNoContent());
    }
}
