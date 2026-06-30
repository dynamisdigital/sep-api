package com.dynamis.sep_api.shared.integration;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    @Test
    void preservaHeaderValidoDuranteRequestELimpaMdcAoFinal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "cliente-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duranteRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> duranteRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(duranteRequest).hasValue("cliente-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("cliente-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void geraUuidQuandoHeaderAusente() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).satisfies(value -> assertThatCodeIsUuid(value));
    }

    @Test
    void substituiHeaderComCaracteresInvalidos() throws Exception {
        MockHttpServletRequest request = mock(MockHttpServletRequest.class);
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("abc\nforjado");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).satisfies(value -> assertThatCodeIsUuid(value));
    }

    @Test
    void substituiHeaderMaiorQueLimite() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "a".repeat(129));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).satisfies(value -> assertThatCodeIsUuid(value));
    }

    private static void assertThatCodeIsUuid(String value) {
        assertThat(value).isNotBlank();
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }
}
