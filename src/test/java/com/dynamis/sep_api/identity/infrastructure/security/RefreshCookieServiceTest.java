package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.identity.application.ClientChannel;
import com.dynamis.sep_api.identity.web.dto.TokenResponseDto;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookieServiceTest {

    private RefreshCookieProperties props;
    private JwtProperties jwtProperties;
    private RefreshCookieService service;

    @BeforeEach
    void setUp() {
        props = new RefreshCookieProperties();
        props.setName("sep-refresh");
        props.setPath("/api/v1/auth");
        props.setSecure(false);
        props.setSameSite("Lax");
        props.setDomain("");

        jwtProperties = new JwtProperties();
        jwtProperties.setRefreshExpirationSeconds(2_592_000L);

        service = new RefreshCookieService(props, jwtProperties);
    }

    private TokenResponseDto sampleBody(String refresh) {
        UsuarioResponseDto usuario = new UsuarioResponseDto(
                UUID.randomUUID(),
                "u@sep.test",
                Role.CLIENTE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "system",
                "system",
                false,
                false);
        return TokenResponseDto.comTokens("access", 900, refresh, usuario);
    }

    @Test
    void canalMobileMantemBodyOriginal() {
        TokenResponseDto body = sampleBody("refresh-cru");

        ResponseEntity<TokenResponseDto> resp = service.emitir(ClientChannel.MOBILE, body);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().refreshToken()).isEqualTo("refresh-cru");
        assertThat(resp.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void canalWebEmiteSetCookieEOmiteRefreshDoBody() {
        TokenResponseDto body = sampleBody("refresh-cru");

        ResponseEntity<TokenResponseDto> resp = service.emitir(ClientChannel.WEB, body);

        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().refreshToken()).isNull();
        assertThat(resp.getBody().accessToken()).isEqualTo("access");
        String cookie = resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie).contains("sep-refresh=refresh-cru");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("Path=/api/v1/auth");
        assertThat(cookie).contains("SameSite=Lax");
        assertThat(cookie).contains("Max-Age=2592000");
    }

    @Test
    void canalWebSemRefreshNaoEmiteCookie() {
        // Login com MFA: refresh ainda nao foi emitido (challenge); nada de Set-Cookie.
        TokenResponseDto body = TokenResponseDto.desafioMfa(UUID.randomUUID());

        ResponseEntity<TokenResponseDto> resp = service.emitir(ClientChannel.WEB, body);

        assertThat(resp.getBody()).isEqualTo(body);
        assertThat(resp.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void cookieDeLimpezaUsaMaxAgeZero() {
        String cookie = service.construirCookieDeLimpeza();

        assertThat(cookie).contains("sep-refresh=");
        assertThat(cookie).contains("Max-Age=0");
        assertThat(cookie).contains("HttpOnly");
    }

    @Test
    void secureTrueAplicaFlagSecure() {
        props.setSecure(true);

        ResponseEntity<TokenResponseDto> resp = service.emitir(ClientChannel.WEB, sampleBody("r"));

        assertThat(resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Secure");
    }

    @Test
    void domainNaoVazioAplicaDomain() {
        props.setDomain(".sep.test");

        ResponseEntity<TokenResponseDto> resp = service.emitir(ClientChannel.WEB, sampleBody("r"));

        assertThat(resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Domain=.sep.test");
    }

    @Test
    void fromHeaderResolveCanalCorretamente() {
        assertThat(ClientChannel.fromHeader("WEB")).isEqualTo(ClientChannel.WEB);
        assertThat(ClientChannel.fromHeader("web")).isEqualTo(ClientChannel.WEB);
        assertThat(ClientChannel.fromHeader("MOBILE")).isEqualTo(ClientChannel.MOBILE);
        assertThat(ClientChannel.fromHeader(null)).isEqualTo(ClientChannel.MOBILE);
        assertThat(ClientChannel.fromHeader("xpto")).isEqualTo(ClientChannel.MOBILE);
    }
}
