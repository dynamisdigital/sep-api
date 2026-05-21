package com.dynamis.sep_api.contratos.web.controller;

import com.dynamis.sep_api.contratos.application.usecase.ProcessarWebhookAssinaturaUseCase;
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

@WebMvcTest(controllers = AssinaturaWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class AssinaturaWebhookControllerTest {

    private static final String PAYLOAD_SIGN =
            "{\"event\":{\"name\":\"sign\",\"occurred_at\":\"2026-05-21T10:00:00Z\"},\"document\":{\"key\":\"DOC-1\"}}";
    private static final String PAYLOAD_REFUSE =
            "{\"event\":{\"name\":\"refuse\",\"occurred_at\":\"2026-05-21T10:00:00Z\"},\"document\":{\"key\":\"DOC-2\"}}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookSignatureValidator signatureValidator;

    @MockBean
    private ProcessarWebhookAssinaturaUseCase processarUseCase;

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
        when(processarUseCase.executar(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new ProcessarWebhookAssinaturaUseCase.Resultado(true, false));
    }

    @Test
    void aceita202CallbackSign() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/assinatura/clicksign")
                        .header("Idempotency-Key", "idem-sign")
                        .header("Content-Hmac", "sha256=abc123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_SIGN))
                .andExpect(status().isAccepted());

        verify(signatureValidator).isValid(eq("clicksign-assinatura"), eq(PAYLOAD_SIGN), eq("abc123"));
        verify(processarUseCase)
                .executar(eq("clicksign"), eq("idem-sign"), anyString(), eq("abc123"), eq(PAYLOAD_SIGN), any());
    }

    @Test
    void aceita202CallbackRefuse() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/assinatura/clicksign")
                        .header("Idempotency-Key", "idem-refuse")
                        .header("Content-Hmac", "sha256=def456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_REFUSE))
                .andExpect(status().isAccepted());
    }

    @Test
    void aceitaContentHmacSemPrefixo() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/assinatura/clicksign")
                        .header("Idempotency-Key", "idem-noprefix")
                        .header("Content-Hmac", "abc999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_SIGN))
                .andExpect(status().isAccepted());

        verify(signatureValidator).isValid(eq("clicksign-assinatura"), eq(PAYLOAD_SIGN), eq("abc999"));
    }

    @Test
    void aceitaHeaderAliasXWebhookSignature() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/assinatura/clicksign")
                        .header("Idempotency-Key", "idem-alias")
                        .header("X-Webhook-Signature", "alias-sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_SIGN))
                .andExpect(status().isAccepted());

        verify(signatureValidator).isValid(eq("clicksign-assinatura"), eq(PAYLOAD_SIGN), eq("alias-sig"));
    }

    @Test
    void derivaIdempotencyKeyQuandoHeaderAusente() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/assinatura/clicksign")
                        .header("Content-Hmac", "sha256=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_SIGN))
                .andExpect(status().isAccepted());

        verify(processarUseCase)
                .executar(eq("clicksign"), anyString(), anyString(), eq("abc"), eq(PAYLOAD_SIGN), any());
    }

    @Test
    void retorna401QuandoHmacInvalido() throws Exception {
        when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/assinatura/clicksign")
                        .header("Idempotency-Key", "idem-hmac")
                        .header("Content-Hmac", "sha256=bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_SIGN))
                .andExpect(status().isUnauthorized());

        verify(processarUseCase, never())
                .executar(anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void retorna400QuandoAssinaturaAusente() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/assinatura/clicksign")
                        .header("Idempotency-Key", "idem-no-sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_SIGN))
                .andExpect(status().isBadRequest());

        verify(signatureValidator, never()).isValid(anyString(), anyString(), anyString());
    }

    @Test
    void retorna400QuandoProviderNaoSuportado() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/assinatura/d4sign")
                        .header("Idempotency-Key", "idem-x")
                        .header("Content-Hmac", "sha256=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_SIGN))
                .andExpect(status().isBadRequest());

        verify(signatureValidator, never()).isValid(anyString(), anyString(), anyString());
    }

    @Test
    void retorna400QuandoPayloadNaoJsonValido() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/assinatura/clicksign")
                        .header("Idempotency-Key", "idem-bad-json")
                        .header("Content-Hmac", "sha256=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest());

        verify(processarUseCase, never())
                .executar(anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }
}
