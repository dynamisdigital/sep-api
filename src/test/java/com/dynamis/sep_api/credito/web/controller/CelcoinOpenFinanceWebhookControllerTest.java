package com.dynamis.sep_api.credito.web.controller;

import com.dynamis.sep_api.credito.application.usecase.ProcessarWebhookOpenFinanceUseCase;
import com.dynamis.sep_api.identity.infrastructure.security.ApiAccessDeniedHandler;
import com.dynamis.sep_api.identity.infrastructure.security.ApiAuthenticationEntryPoint;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
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

@WebMvcTest(controllers = CelcoinOpenFinanceWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class CelcoinOpenFinanceWebhookControllerTest {

    private static final String PAYLOAD_AUTORIZADO = "{\"type\":\"consent.authorized\",\"consent_id\":\"ext-of-1\"}";
    private static final String PAYLOAD_NEGADO =
            "{\"type\":\"consent.denied\",\"consent_id\":\"ext-of-2\",\"reason\":\"user denied\"}";
    private static final String PAYLOAD_MOVIMENTACAO = "{\"type\":\"movement.data\",\"consent_id\":\"ext-of-3\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookSignatureValidator signatureValidator;

    @MockBean
    private ProcessarWebhookOpenFinanceUseCase processarUseCase;

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
        when(processarUseCase.executar(anyString(), anyString(), anyString(), any()))
                .thenReturn(new ProcessarWebhookOpenFinanceUseCase.Resultado(true, false));
    }

    @Test
    void aceita202CallbackAutorizado() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/open-finance")
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Webhook-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_AUTORIZADO))
                .andExpect(status().isAccepted());

        verify(processarUseCase).executar(eq("idem-1"), eq("abc"), eq(PAYLOAD_AUTORIZADO), any());
    }

    @Test
    void aceita202CallbackNegado() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/open-finance")
                        .header("Idempotency-Key", "idem-2")
                        .header("X-Webhook-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_NEGADO))
                .andExpect(status().isAccepted());
    }

    @Test
    void aceita202CallbackMovimentacao() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/open-finance")
                        .header("Idempotency-Key", "idem-3")
                        .header("X-Webhook-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_MOVIMENTACAO))
                .andExpect(status().isAccepted());
    }

    @Test
    void aceita202QuandoHeaderAliasXCelcoinSignature() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/open-finance")
                        .header("Idempotency-Key", "idem-alias")
                        .header("X-Celcoin-Signature", "xyz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_AUTORIZADO))
                .andExpect(status().isAccepted());

        verify(processarUseCase).executar(eq("idem-alias"), eq("xyz"), eq(PAYLOAD_AUTORIZADO), any());
    }

    @Test
    void retorna400QuandoIdempotencyKeyAusente() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/open-finance")
                        .header("X-Webhook-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_AUTORIZADO))
                .andExpect(status().isBadRequest());

        verify(processarUseCase, never()).executar(anyString(), anyString(), anyString(), any());
    }

    @Test
    void retorna400QuandoSignatureAusente() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/open-finance")
                        .header("Idempotency-Key", "idem-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_AUTORIZADO))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna401QuandoHmacInvalido() throws Exception {
        when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/celcoin/open-finance")
                        .header("Idempotency-Key", "idem-hmac")
                        .header("X-Webhook-Signature", "bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_AUTORIZADO))
                .andExpect(status().isUnauthorized());

        verify(processarUseCase, never()).executar(anyString(), anyString(), anyString(), any());
    }

    @Test
    void retorna400QuandoPayloadNaoJsonValido() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/celcoin/open-finance")
                        .header("Idempotency-Key", "idem-bad-json")
                        .header("X-Webhook-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest());

        verify(processarUseCase, never()).executar(anyString(), anyString(), anyString(), any());
    }
}
