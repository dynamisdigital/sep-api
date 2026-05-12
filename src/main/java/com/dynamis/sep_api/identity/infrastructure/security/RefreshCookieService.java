package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.identity.application.ClientChannel;
import com.dynamis.sep_api.identity.web.dto.TokenResponseDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Aplica o cookie HttpOnly de refresh token para clientes WEB e devolve o corpo apropriado
 * (follow-up 5F-FIX-02 da Sprint 5).
 *
 * <ul>
 *   <li>WEB: refresh viaja em {@code Set-Cookie HttpOnly}; body de {@link TokenResponseDto} omite
 *       o refresh para impedir leitura via JS / XSS.
 *   <li>MOBILE: refresh continua no body (Capacitor Preferences faz a persistencia nativa); cookie
 *       nao e aplicado.
 * </ul>
 */
@Service
public class RefreshCookieService {

    private final RefreshCookieProperties props;
    private final JwtProperties jwtProperties;

    public RefreshCookieService(RefreshCookieProperties props, JwtProperties jwtProperties) {
        this.props = props;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Decora a resposta com {@code Set-Cookie} quando o canal for WEB e remove o refresh do body;
     * para MOBILE, devolve a resposta original sem alteracao. Quando {@code body.refreshToken()}
     * for {@code null} (ex.: desafio MFA), nao emite cookie.
     */
    public ResponseEntity<TokenResponseDto> emitir(ClientChannel canal, TokenResponseDto body) {
        if (canal != ClientChannel.WEB || body == null || body.refreshToken() == null) {
            return ResponseEntity.ok(body);
        }
        ResponseCookie cookie = construirCookie(body.refreshToken(), jwtProperties.getRefreshExpirationSeconds());
        TokenResponseDto bodySemRefresh = new TokenResponseDto(
                body.accessToken(),
                body.tokenType(),
                body.expiresIn(),
                null,
                body.usuario(),
                body.mfaRequired(),
                body.mfaChallengeId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(bodySemRefresh);
    }

    /** Constroi o cookie de limpeza (max-age=0) para logout WEB; idempotente em MOBILE. */
    public String construirCookieDeLimpeza() {
        return construirCookie("", 0).toString();
    }

    private ResponseCookie construirCookie(String valor, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(props.getName(), valor)
                .httpOnly(true)
                .secure(props.isSecure())
                .path(props.getPath())
                .sameSite(props.getSameSite())
                .maxAge(Duration.ofSeconds(maxAgeSeconds));
        if (props.getDomain() != null && !props.getDomain().isBlank()) {
            builder.domain(props.getDomain());
        }
        return builder.build();
    }
}
