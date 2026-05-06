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
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ApiAccessDeniedHandler handler = new ApiAccessDeniedHandler(objectMapper);

    @BeforeEach
    void setUp() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "trace-deny");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void serializaErrorResponseEm403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("nope"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("error").asText()).isEqualTo("Forbidden");
        assertThat(body.get("message").asText()).isEqualTo("Acesso negado");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/usuarios");
        assertThat(body.get("traceId").asText()).isEqualTo("trace-deny");
    }
}
