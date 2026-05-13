package com.dynamis.sep_api.onboarding.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.ApiAccessDeniedHandler;
import com.dynamis.sep_api.identity.infrastructure.security.ApiAuthenticationEntryPoint;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.onboarding.application.usecase.ProcessarCallbackKycUseCase;
import com.dynamis.sep_api.shared.application.port.out.WebhookSignatureValidator;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CelcoinKycWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class CelcoinKycWebhookControllerTest {

    private static final String PAYLOAD = "{\"verification_id\":\"ext-1\",\"status\":\"APPROVED\",\"reason\":null}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookSignatureValidator signatureValidator;

    @MockBean
    private ProcessarCallbackKycUseCase processarCallbackUseCase;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

    @MockBean
    private ApiAccessDeniedHandler apiAccessDeniedHandler;

    @BeforeEach
    void setup() {
        when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(true);
        when(processarCallbackUseCase.executar(anyString(), anyString(), anyString(), any()))
                .thenReturn(new ProcessarCallbackKycUseCase.Resultado(true, false));
    }

    @Test
    void aceita202QuandoAssinaturaValidaEHeadersPresentes() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/kyc")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Webhook-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isAccepted());

        verify(processarCallbackUseCase).executar(eq("idem-1"), eq("abc"), eq(PAYLOAD), any());
    }

    @Test
    void aceita202QuandoUsadoHeaderAliasXCelcoinSignature() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/kyc")
                        .header("Idempotency-Key", "idem-2")
                        .header("X-Celcoin-Signature", "xyz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isAccepted());

        verify(processarCallbackUseCase).executar(eq("idem-2"), eq("xyz"), eq(PAYLOAD), any());
    }

    @Test
    void retorna400SemHeaderIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/kyc")
                        .header("X-Webhook-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest());

        verify(processarCallbackUseCase, never()).executar(anyString(), anyString(), anyString(), any());
    }

    @Test
    void retorna400SemQualquerSignature() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/kyc")
                        .header("Idempotency-Key", "idem-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest());

        verify(processarCallbackUseCase, never()).executar(anyString(), anyString(), anyString(), any());
    }

    @Test
    void retorna401SeAssinaturaInvalida() throws Exception {
        when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/celcoin/kyc")
                        .header("Idempotency-Key", "idem-4")
                        .header("X-Webhook-Signature", "fake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());

        verify(processarCallbackUseCase, never()).executar(anyString(), anyString(), anyString(), any());
    }

    @Test
    void retorna400SeBodyNaoEJsonValido() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/kyc")
                        .header("Idempotency-Key", "idem-5")
                        .header("X-Webhook-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest());
    }
}
