package com.dynamis.sep_api.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobertura de CORS apos o follow-up 5F-FIX-03 (Sprint 5): o preflight de rotas autenticadas com
 * step-up deve permitir o header {@code X-Step-Up-Token}; sem isso o navegador bloqueia a chamada
 * antes do PATCH chegar no servidor.
 *
 * <p>O MockMvc usa {@code apply(springSecurity())} para incluir o {@code CorsFilter} montado pelo
 * Spring Security; sem isso o preflight nao atinge o filtro de CORS.
 */
@SpringBootTest
@ActiveProfiles("dev")
class CorsConfigTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void preflightAlterarSenhaPermiteStepUpToken() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(options("/api/v1/usuarios/1f0799c0-98b9-6d9d-bc4a-7d6f5b771001/senha")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type,x-step-up-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().stringValues(
                                "Access-Control-Allow-Headers",
                                org.hamcrest.Matchers.hasItem(
                                        org.hamcrest.Matchers.containsStringIgnoringCase("x-step-up-token"))));
    }
}
