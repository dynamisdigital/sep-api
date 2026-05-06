package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    // 32 bytes = 256 bits (HS256 minimo)
    private static final String SECRET_BASE64 =
            Base64.getEncoder().encodeToString("test-secret-32-bytes-padding-aaa".getBytes(StandardCharsets.UTF_8));

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET_BASE64, 3600);
    }

    @Test
    void geraTokenComClaimsObrigatorias() {
        Usuario usuario = Usuario.criar("admin@sep.test", "$2a$hash", Role.ADMIN);

        String token = provider.gerarToken(usuario);

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_BASE64)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertThat(claims.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(claims.get("email", String.class)).isEqualTo("admin@sep.test");
        assertThat(claims.get("roles", List.class)).containsExactly("ROLE_ADMIN");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void extraiPrincipalComIdUsernameERole() {
        Usuario usuario = Usuario.criar("cliente@sep.test", "$2a$hash", Role.CLIENTE);
        String token = provider.gerarToken(usuario);

        UsuarioAutenticado principal = provider.extrairPrincipal(token);

        assertThat(principal.id()).isEqualTo(usuario.getId());
        assertThat(principal.username()).isEqualTo("cliente@sep.test");
        assertThat(principal.role()).isEqualTo(Role.CLIENTE);
    }

    @Test
    void rejeitaTokenExpirado() throws Exception {
        Usuario usuario = Usuario.criar("admin@sep.test", "$2a$hash", Role.ADMIN);
        String tokenExpirado = Jwts.builder()
                .subject(usuario.getId().toString())
                .claim("email", usuario.getUsername())
                .claim("roles", List.of("ROLE_ADMIN"))
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_BASE64)), Jwts.SIG.HS256)
                .compact();

        assertThat(provider.tokenValido(tokenExpirado)).isFalse();
    }

    @Test
    void rejeitaTokenAssinadoComOutroSecret() {
        Usuario usuario = Usuario.criar("admin@sep.test", "$2a$hash", Role.ADMIN);
        String outroSecret =
                Base64.getEncoder().encodeToString("outro-secret-32-bytes-padding-bb".getBytes(StandardCharsets.UTF_8));
        JwtTokenProvider outroProvider = new JwtTokenProvider(outroSecret, 3600);
        String tokenAssinadoComOutro = outroProvider.gerarToken(usuario);

        assertThat(provider.tokenValido(tokenAssinadoComOutro)).isFalse();
    }
}
