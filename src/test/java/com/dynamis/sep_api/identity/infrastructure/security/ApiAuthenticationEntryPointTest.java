package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(objectMapper);

    @BeforeEach
    void setUp() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "trace-xyz");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void serializaErrorResponseEm401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("nope"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("message").asText()).isEqualTo("Autenticacao requerida");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/auth/me");
        assertThat(body.get("traceId").asText()).isEqualTo("trace-xyz");
    }
}
