package com.dynamis.sep_api.shared.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.ApiAccessDeniedHandler;
import com.dynamis.sep_api.identity.infrastructure.security.ApiAuthenticationEntryPoint;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.shared.application.port.out.WebhookSignatureValidator;
import com.dynamis.sep_api.shared.application.usecase.RegistrarWebhookEventUseCase;
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

@WebMvcTest(controllers = WebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookSignatureValidator signatureValidator;

    @MockBean
    private RegistrarWebhookEventUseCase registrarWebhookEventUseCase;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

    @MockBean
    private ApiAccessDeniedHandler apiAccessDeniedHandler;

    @BeforeEach
    void setUp() {
        when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(true);
        when(registrarWebhookEventUseCase.executar(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(true);
    }

    @Test
    void eventoNovoRetorna202() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/pagamento_recebido")
                        .header("Idempotency-Key", "k1")
                        .header("X-Webhook-Signature", "deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"abc\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void eventoDuplicadoRetorna202() throws Exception {
        when(registrarWebhookEventUseCase.executar(
                        eq("celcoin"), eq("pagamento_recebido"), eq("k1"), any(), anyString()))
                .thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/celcoin/pagamento_recebido")
                        .header("Idempotency-Key", "k1")
                        .header("X-Webhook-Signature", "deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void semIdempotencyKeyRetorna400() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/pagamento_recebido")
                        .header("X-Webhook-Signature", "deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(registrarWebhookEventUseCase, never()).executar(any(), any(), any(), any(), any());
    }

    @Test
    void semSignatureRetorna400() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/pagamento_recebido")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(registrarWebhookEventUseCase, never()).executar(any(), any(), any(), any(), any());
    }

    @Test
    void assinaturaInvalidaRetorna401() throws Exception {
        when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/celcoin/pagamento_recebido")
                        .header("Idempotency-Key", "k1")
                        .header("X-Webhook-Signature", "deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verify(registrarWebhookEventUseCase, never()).executar(any(), any(), any(), any(), any());
    }
}
