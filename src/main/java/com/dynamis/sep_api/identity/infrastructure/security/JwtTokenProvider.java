package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Emite e valida tokens JWT (HS256) para autenticacao da API SEP.
 *
 * <p>Secret lido de {@code app.jwt.secret} interpretado como Base64 quando possivel; senao usa
 * bytes UTF-8 (compatibilidade com placeholder dev). Minimo 256 bits exigido pelo JJWT.
 *
 * <p>Claims emitidas: {@code sub} (UUID v6 canonico), {@code email}, {@code roles}, {@code iat},
 * {@code exp}. JJWT 0.12.x usa {@code Jwts.parser().verifyWith(key)}.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration-seconds:${app.jwt.expiration-seconds:900}}") long expirationSeconds) {
        this.secretKey = buildKey(secret);
        this.expirationSeconds = expirationSeconds;
    }

    private static SecretKey buildKey(String secret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
            if (bytes.length < 32) {
                bytes = secret.getBytes(StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException ex) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret precisa codificar pelo menos 256 bits (32 bytes); valor atual"
                            + " tem "
                            + bytes.length
                            + " bytes");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String gerarToken(Usuario usuario) {
        Instant agora = Instant.now();
        Instant expiracao = agora.plusSeconds(expirationSeconds);
        String correlationId = MDC.get("correlationId");
        log.debug("Emitindo JWT para usuario {} (correlationId={})", usuario.getId(), correlationId);
        return Jwts.builder()
                .subject(usuario.getId().toString())
                .claim("email", usuario.getUsername())
                .claim("roles", List.of("ROLE_" + usuario.getRole().name()))
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiracao))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public boolean tokenValido(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            String correlationId = MDC.get("correlationId");
            log.debug(
                    "Token JWT rejeitado: {} (correlationId={})", ex.getClass().getSimpleName(), correlationId);
            return false;
        }
    }

    public UsuarioAutenticado extrairPrincipal(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        UUID id = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        Role role = extrairRole(claims);
        return new UsuarioAutenticado(id, email, role);
    }

    private static Role extrairRole(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> lista && !lista.isEmpty()) {
            String primeiro = lista.get(0).toString();
            String semPrefixo = primeiro.startsWith("ROLE_") ? primeiro.substring(5) : primeiro;
            return Role.valueOf(semPrefixo);
        }
        throw new IllegalArgumentException("Claim 'roles' ausente ou vazia no token");
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
