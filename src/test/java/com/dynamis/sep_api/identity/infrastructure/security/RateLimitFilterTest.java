package com.dynamis.sep_api.identity.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setup() {
        RateLimitProperties props = new RateLimitProperties();
        props.setLoginPerMinutePerIp(2);
        props.setTotpVerifyPerMinutePerIp(2);
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new RateLimitFilter(props, om);
    }

    private MockHttpServletRequest req(String path, String method, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(path);
        req.setMethod(method);
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void requestForaDosCaminhosProtegidosPassa() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = req("/api/v1/usuarios", "GET", "1.1.1.1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void primeirasRequestsLoginPassam() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest r = req("/api/v1/auth/login", "POST", "9.9.9.9");
            filter.doFilter(r, new MockHttpServletResponse(), chain);
        }

        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void excederLimiteLoginRetorna429() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 2; i++) {
            filter.doFilter(req("/api/v1/auth/login", "POST", "8.8.8.8"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse extraRes = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/auth/login", "POST", "8.8.8.8"), extraRes, chain);

        assertThat(extraRes.getStatus()).isEqualTo(429);
        assertThat(extraRes.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void excederLimiteTotpVerifyRetorna429() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 2; i++) {
            filter.doFilter(req("/api/v1/auth/totp/verify", "POST", "7.7.7.7"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse extraRes = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/auth/totp/verify", "POST", "7.7.7.7"), extraRes, chain);

        assertThat(extraRes.getStatus()).isEqualTo(429);
    }

    @Test
    void extrairIpUsaXForwardedForQuandoPresente() {
        MockHttpServletRequest r = req("/p", "GET", "1.1.1.1");
        r.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        assertThat(RateLimitFilter.extrairIp(r)).isEqualTo("203.0.113.10");
    }
}
