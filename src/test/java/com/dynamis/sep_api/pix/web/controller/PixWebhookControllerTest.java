package com.dynamis.sep_api.pix.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.ApiAccessDeniedHandler;
import com.dynamis.sep_api.identity.infrastructure.security.ApiAuthenticationEntryPoint;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.pix.application.usecase.ProcessarWebhookPixUseCase;
import com.dynamis.sep_api.shared.application.port.out.WebhookSignatureValidator;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Os dois 400 do webhook Pix nao tinham teste de unidade: o {@code PixWebhookIT} cobre so o status do
 * primeiro, e o de body vazio nao era exercitado em lugar nenhum.
 */
@WebMvcTest(controllers = PixWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class PixWebhookControllerTest {

    private static final String URL = "/api/v1/webhooks/celcoin/pix";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookSignatureValidator signatureValidator;

    @MockBean
    private ProcessarWebhookPixUseCase processarUseCase;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

    @MockBean
    private ApiAccessDeniedHandler apiAccessDeniedHandler;

    @Test
    void retorna400SemAssinatura() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"evt-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("WHK-400-003"))
                .andExpect(jsonPath("$.message")
                        .value("Header X-Webhook-Signature (ou X-Celcoin-Signature) e obrigatorio"));
        verifyNoInteractions(signatureValidator, processarUseCase);
    }

    @Test
    void retorna400ComBodyEmBranco() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-Webhook-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("WHK-400-004"))
                .andExpect(jsonPath("$.message").value("Body do webhook e obrigatorio"));
        verifyNoInteractions(signatureValidator, processarUseCase);
    }
}
