package com.dynamis.sep_api.onboarding.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.ApiAccessDeniedHandler;
import com.dynamis.sep_api.identity.infrastructure.security.ApiAuthenticationEntryPoint;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.onboarding.application.usecase.ProcessarCallbackPldUseCase;
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

@WebMvcTest(controllers = CelcoinPldWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class CelcoinPldWebhookControllerTest {

    private static final String PAYLOAD = "{\"external_id\":\"00000000-0000-0000-0000-000000000001\",\"results\":[]}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookSignatureValidator signatureValidator;

    @MockBean
    private ProcessarCallbackPldUseCase processarCallbackUseCase;

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
                .thenReturn(new ProcessarCallbackPldUseCase.Resultado(true, false));
    }

    @Test
    void aceita202QuandoAssinaturaValidaEHeadersPresentes() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/pld")
                        .header("Idempotency-Key", "idem-pld-1")
                        .header("X-Webhook-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isAccepted());

        verify(processarCallbackUseCase).executar(eq("idem-pld-1"), eq("abc"), eq(PAYLOAD), any());
    }

    @Test
    void retorna400SemHeaderIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/pld")
                        .header("X-Webhook-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest());

        verify(processarCallbackUseCase, never()).executar(anyString(), anyString(), anyString(), any());
    }

    @Test
    void retorna401SeAssinaturaInvalida() throws Exception {
        when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/celcoin/pld")
                        .header("Idempotency-Key", "idem-pld-2")
                        .header("X-Webhook-Signature", "fake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());

        verify(processarCallbackUseCase, never()).executar(anyString(), anyString(), anyString(), any());
    }

    @Test
    void aceita202QuandoUseCaseRetornaDuplicadoIdempotente() throws Exception {
        when(processarCallbackUseCase.executar(anyString(), anyString(), anyString(), any()))
                .thenReturn(new ProcessarCallbackPldUseCase.Resultado(true, true));

        mockMvc.perform(post("/api/v1/webhooks/celcoin/pld")
                        .header("Idempotency-Key", "idem-pld-dup")
                        .header("X-Webhook-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isAccepted());

        verify(processarCallbackUseCase).executar(eq("idem-pld-dup"), eq("abc"), eq(PAYLOAD), any());
    }

    @Test
    void retorna400SeBodyNaoEJsonValido() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/pld")
                        .header("Idempotency-Key", "idem-pld-3")
                        .header("X-Webhook-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest());
    }
}
